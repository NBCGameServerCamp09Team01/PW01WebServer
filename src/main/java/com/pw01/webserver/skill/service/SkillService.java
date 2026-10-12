package com.pw01.webserver.skill.service;

import com.pw01.webserver.account.service.AccountService;
import com.pw01.webserver.account.service.ProgressWriter;
import com.pw01.webserver.account.service.SkillUnlockResult;
import com.pw01.webserver.common.idempotency.IdempotencyService;
import com.pw01.webserver.progression.requirement.AccountFacts;
import com.pw01.webserver.progression.requirement.RequirementProgress;
import com.pw01.webserver.skill.dto.SkillListResponse;
import com.pw01.webserver.skill.dto.SkillUnlockRequest;
import com.pw01.webserver.skill.dto.SkillUnlockResponse;
import com.pw01.webserver.skill.master.SkillsFile;
import com.pw01.webserver.stageresult.dto.SaveStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 스킬 해금(K1)과 진행도 조회(K2), 루트 docs/contracts/skill-api.md.
 * 해금: 중복 방지(SF-5) → 스킬 키 → 계정 기능의 판정·저장(ProgressWriter 트랜잭션, SF-6) → 오류 코드·응답.
 * 판정·저장은 계정 데이터의 주인인 AccountService.unlockSkill이 하고, 조건 판정 방법은 SkillJudge를 넘긴다.
 * 응답은 저장이 끝난 뒤 getSnapshot으로 읽어 올라간 version을 담는다.
 */
@Service
@RequiredArgsConstructor
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private final SkillCatalog skillCatalog;
    private final SkillJudge skillJudge;
    private final AccountService accountService;
    private final ProgressWriter progressWriter;
    private final IdempotencyService idempotencyService;

    public SkillUnlockResponse unlock(Long accountId, SkillUnlockRequest request) {
        String requestId = request.requestId().toLowerCase(Locale.ROOT);
        return idempotencyService.run(accountId, requestId, request, SkillUnlockResponse.class,
                () -> doUnlock(accountId, requestId, request.skillTag()));
    }

    /** K2: 스킬마다 상태와 조건 진행도(서버 판정). 상태를 바꾸지 않는다 */
    public SkillListResponse list(Long accountId) {
        AccountFacts facts = accountService.facts(accountId);
        List<SkillListResponse.SkillView> views = skillCatalog.all().stream()
                .map(skill -> view(skill, facts))
                .toList();
        return new SkillListResponse(views, facts.statPoints(), facts.version());
    }

    private SkillUnlockResponse doUnlock(Long accountId, String requestId, String skillTag) {
        SkillsFile.Skill skill = skillCatalog.find(skillTag).orElseThrow(() -> SkillErrors.unknown(skillTag));
        SkillUnlockResult result = progressWriter.run("skill-unlock requestId=" + requestId,
                () -> accountService.unlockSkill(accountId, requestId, skill.tag(), skill.cost(),
                        facts -> skillJudge.judge(skill, facts)));

        SaveStatus saveStatus = switch (result.outcome()) {
            case SAVED -> SaveStatus.SAVED;
            case ALREADY_SAVED -> SaveStatus.ALREADY_SAVED;
            case ALREADY_UNLOCKED -> throw SkillErrors.alreadyUnlocked();
            case REQUIREMENTS_NOT_MET -> {
                log.info("해금 거절: 조건 미충족 requestId={} skill={} unmet={}", requestId, skillTag,
                        result.requirements().stream().filter(r -> !r.met()).map(RequirementProgress::type).toList());
                throw SkillErrors.requirementsNotMet(result.requirements());
            }
        };
        log.info("해금 {} requestId={} skill={} cost={}", saveStatus, requestId, skillTag, skill.cost());
        return SkillUnlockResponse.of(skillTag, saveStatus, accountService.getSnapshot(accountId));
    }

    private SkillListResponse.SkillView view(SkillsFile.Skill skill, AccountFacts facts) {
        List<RequirementProgress> requirements = skillJudge.judge(skill, facts);
        String state = skillJudge.state(skill, facts, requirements);
        return new SkillListResponse.SkillView(skill.tag(), state, skill.cost(),
                SkillJudge.UNLOCKED.equals(state) ? List.of() : requirements);
    }

}
