package com.pw01.webserver.stat.service;

import com.pw01.webserver.account.service.AccountService;
import com.pw01.webserver.account.service.ProgressWriter;
import com.pw01.webserver.account.service.StatChange;
import com.pw01.webserver.account.service.StatChangeResult;
import com.pw01.webserver.common.error.ErrorResponse.FieldErrorDetail;
import com.pw01.webserver.common.idempotency.IdempotencyService;
import com.pw01.webserver.config.StatInvestProperties;
import com.pw01.webserver.stageresult.dto.SaveStatus;
import com.pw01.webserver.stat.dto.StatInvestRequest;
import com.pw01.webserver.stat.dto.StatInvestResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 묶음 스탯 투자(루트 docs/contracts/stat-api.md T1): 형식 → 중복 방지(SF-5) → 스탯 키 → 계정 기능의 판정·저장 → 응답.
 * 판정·저장은 계정 데이터의 주인인 AccountService.changeStats가 하고, 이 서비스는 오류 코드와 응답을 정한다.
 * 트랜잭션과 충돌 재시도는 ProgressWriter가 맡는다(SF-6). 응답은 저장이 끝난 뒤 getSnapshot으로 읽어 올라간 version을 담는다.
 */
@Service
@RequiredArgsConstructor
public class StatInvestService {

    private static final Logger log = LoggerFactory.getLogger(StatInvestService.class);

    private final StatCatalog statCatalog;
    private final AccountService accountService;
    private final ProgressWriter progressWriter;
    private final IdempotencyService idempotencyService;
    private final StatInvestProperties statInvestProperties;

    public StatInvestResponse invest(Long accountId, StatInvestRequest request) {
        validateShape(request);
        String requestId = request.requestId().toLowerCase(Locale.ROOT);
        return idempotencyService.run(accountId, requestId, request, StatInvestResponse.class,
                () -> doInvest(accountId, requestId, request));
    }

    private StatInvestResponse doInvest(Long accountId, String requestId, StatInvestRequest request) {
        List<StatChange> changes = toChanges(request);
        StatChangeResult result = progressWriter.run("stat-invest requestId=" + requestId,
                () -> accountService.changeStats(accountId, requestId, changes));

        SaveStatus saveStatus = switch (result.outcome()) {
            case SAVED -> SaveStatus.SAVED;
            case ALREADY_SAVED -> SaveStatus.ALREADY_SAVED;
            case POINTS_NOT_ENOUGH -> {
                log.info("투자 거절: 포인트 부족 requestId={} need={} have={}", requestId, result.needPoints(),
                        result.havePoints());
                throw StatErrors.pointsNotEnough();
            }
            case MAX_EXCEEDED -> throw StatErrors.maxExceeded(violationErrors(result,
                    "최대 %d포인트입니다(지금 %d, 요청 %+d)."));
            case BELOW_ZERO -> throw StatErrors.belowZero(violationErrors(result,
                    "지금 %2$d포인트라 %3$+d만큼 바꿀 수 없습니다."));
        };
        log.info("투자 {} requestId={} changes={}", saveStatus, requestId, changes.size());
        return StatInvestResponse.of(saveStatus, accountService.getSnapshot(accountId));
    }

    /** 400 형식: 묶음 크기(설정 값), points 0, 같은 스탯 두 번. 칸마다 errors에 담는다 */
    private void validateShape(StatInvestRequest request) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        List<StatInvestRequest.Investment> items = request.investments();
        if (items.size() > statInvestProperties.maxEntries()) {
            errors.add(new FieldErrorDetail("investments",
                    "한 번에 " + statInvestProperties.maxEntries() + "개까지 보낼 수 있습니다."));
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            StatInvestRequest.Investment item = items.get(i);
            if (item.points() == 0) {
                errors.add(new FieldErrorDetail("investments[" + i + "].points", "points는 0이 아닌 정수여야 합니다."));
            }
            if (!seen.add(item.statTag())) {
                errors.add(new FieldErrorDetail(StatErrors.field(i), "같은 스탯을 두 번 보낼 수 없습니다."));
            }
        }
        if (!errors.isEmpty()) {
            throw StatErrors.invalid(errors);
        }
    }

    /** 400 STAT_UNKNOWN: 없는 스탯을 모두 모아 알린다. 있으면 최대 포인트를 붙여 계정 기능에 넘긴다 */
    private List<StatChange> toChanges(StatInvestRequest request) {
        List<StatChange> changes = new ArrayList<>();
        List<FieldErrorDetail> unknown = new ArrayList<>();
        for (int i = 0; i < request.investments().size(); i++) {
            StatInvestRequest.Investment item = request.investments().get(i);
            Optional<Integer> max = statCatalog.maxPoints(item.statTag());
            if (max.isEmpty()) {
                unknown.add(new FieldErrorDetail(StatErrors.field(i), item.statTag() + "은(는) 투자할 수 있는 스탯이 아닙니다."));
            } else {
                changes.add(new StatChange(i, item.statTag(), item.points(), max.get()));
            }
        }
        if (!unknown.isEmpty()) {
            throw StatErrors.unknown(unknown);
        }
        return changes;
    }

    /** 문구 형식 인자: 1 = 최대, 2 = 지금 값, 3 = 요청 points */
    private static List<FieldErrorDetail> violationErrors(StatChangeResult result, String format) {
        return result.violations().stream()
                .map(v -> new FieldErrorDetail(StatErrors.field(v.change().index()),
                        v.change().statKey() + ": " + String.format(format, v.change().maxPoints(), v.current(),
                                v.change().points())))
                .toList();
    }

}
