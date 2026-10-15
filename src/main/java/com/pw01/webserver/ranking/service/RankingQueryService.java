package com.pw01.webserver.ranking.service;

import com.pw01.webserver.account.service.AccountService;
import com.pw01.webserver.common.error.ErrorResponse.FieldErrorDetail;
import com.pw01.webserver.config.RankingProperties;
import com.pw01.webserver.ranking.dto.MyRankingResponse;
import com.pw01.webserver.ranking.dto.RankingBoardResponse;
import com.pw01.webserver.ranking.dto.RankingEntryResponse;
import com.pw01.webserver.ranking.dto.RankingListResponse;
import com.pw01.webserver.ranking.repository.RankingBoardQuery;
import com.pw01.webserver.ranking.repository.RankingRedisStore;
import com.pw01.webserver.ranking.repository.RankingRedisStore.Page;
import com.pw01.webserver.stage.service.RegionDef;
import com.pw01.webserver.stage.service.StageCatalog;
import com.pw01.webserver.stage.service.StageDef;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 랭킹 조회(RK1~RK8). Redis 보드가 있으면 Redis로, 없으면 MySQL에서 다시 채운 뒤 Redis로,
 * 다시 채울 수 없거나(다른 서버가 채우는 중) 랭킹 Redis 명령이 실패하면 MySQL로 답한다(ranking-api.md "장애 때 응답").
 * 두 길은 같은 줄·같은 순위를 낸다(순서 규칙과 값 커서가 같음). Redis 전체 장애는 이 앞의 인증에서 이미 503이다.
 * 닉네임은 계정 기능의 Service(AccountService.getNicknames)로 한 번에 읽는다.
 */
@Service
@RequiredArgsConstructor
public class RankingQueryService {

    private static final Logger log = LoggerFactory.getLogger(RankingQueryService.class);

    private final StageCatalog stageCatalog;
    private final RankingRedisStore redisStore;
    private final RankingBoardQuery boardQuery;
    private final RankingBoardRebuilder rebuilder;
    private final RankingProperties properties;
    private final AccountService accountService;

    /** RK1·RK3·RK5 */
    public RankingListResponse list(RankingBoardKind kind, String targetId, Integer difficulty, String period,
                                    Integer limit, String cursor) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        RankingBoard board = resolveBoard(kind, targetId, difficulty, period, errors);
        int size = limit == null ? properties.pageSize() : limit;
        if (size < 1 || size > properties.maxPageSize()) {
            errors.add(new FieldErrorDetail("limit", "1 이상 " + properties.maxPageSize() + " 이하여야 합니다."));
        }
        if (!errors.isEmpty()) {
            throw RankingErrors.invalidQuery(errors);
        }
        RankingRow after = cursor == null ? null : RankingCursor.decode(board, cursor);

        Page page = read(board, () -> redisStore.page(board, after, size + 1), () -> {
            long before = after == null ? 0 : boardQuery.countAtOrBefore(board, after);
            return new Page(before, boardQuery.page(board, after, size + 1));
        });
        boolean hasNext = page.rows().size() > size;
        List<RankingRow> rows = hasNext ? page.rows().subList(0, size) : page.rows();
        Map<Long, String> nicknames = nicknames(rows);
        List<RankingEntryResponse> items = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            RankingRow row = rows.get(i);
            items.add(RankingEntryResponse.of(board.kind(), page.start() + i + 1, row, nicknames.get(row.accountId())));
        }
        String nextCursor = hasNext ? RankingCursor.encode(board, rows.getLast()) : null;
        return new RankingListResponse(RankingBoardResponse.from(board), items, hasNext, nextCursor);
    }

    /** RK2·RK4·RK6 */
    public MyRankingResponse me(RankingBoardKind kind, String targetId, Integer difficulty, String period,
                                long accountId) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        RankingBoard board = resolveBoard(kind, targetId, difficulty, period, errors);
        if (!errors.isEmpty()) {
            throw RankingErrors.invalidQuery(errors);
        }
        RankingRedisStore.Mine mine = mine(board, accountId);
        return MyRankingResponse.of(board, mine.rank(), mine.row(), mine.total());
    }

    private RankingRedisStore.Mine mine(RankingBoard board, long accountId) {
        return read(board, () -> redisStore.me(board, accountId), () -> {
            RankingRow row = boardQuery.findRow(board, accountId).orElse(null);
            long total = boardQuery.count(board);
            return new RankingRedisStore.Mine(total, row == null ? null : boardQuery.countBefore(board, row) + 1, row);
        });
    }

    /**
     * Redis로 읽고, 보드가 없으면 다시 채운 뒤 한 번 더, 그래도 안 되거나 랭킹 Redis 명령이 실패하면 MySQL로 읽는다.
     * 이 서버에 "다시 채울 보드" 표시가 있으면 먼저 다시 채운다(실패하면 MySQL).
     */
    private <T> T read(RankingBoard board, Supplier<T> fromRedis, Supplier<T> fromMySql) {
        try {
            if (rebuilder.isStale(board) && !rebuilder.rebuild(board)) {
                return fromMySql.get();
            }
            T value = fromRedis.get();
            if (value == null && rebuilder.rebuild(board)) {
                value = fromRedis.get();
            }
            if (value != null) {
                return value;
            }
        } catch (DataAccessException e) {
            log.warn("랭킹 Redis를 쓰지 못해 MySQL로 답함 board={} cause={}", board.id(), e.getClass().getSimpleName());
        }
        return fromMySql.get();
    }

    private RankingBoard resolveBoard(RankingBoardKind kind, String targetId, Integer difficulty, String period,
                                      List<FieldErrorDetail> errors) {
        Instant now = Instant.now();
        return switch (kind) {
            case STAGE -> {
                StageDef stage = stageCatalog.get(targetId);
                yield RankingBoard.stage(stage.stageId(), checkDifficulty(difficulty, errors),
                        checkPeriod(period, errors), now);
            }
            case REGION -> {
                RegionDef region = stageCatalog.getRegion(targetId);
                yield RankingBoard.region(region.regionId(), checkDifficulty(difficulty, errors),
                        checkPeriod(period, errors), now);
            }
            case TOTAL -> RankingBoard.total(checkDifficulty(difficulty, errors), checkPeriod(period, errors), now);
            case PROGRESS -> {
                if (checkPeriod(period, errors) != RankingPeriod.ALL) {
                    errors.add(new FieldErrorDetail("period", "진행 순위는 all만 있습니다."));
                }
                yield RankingBoard.progress();
            }
        };
    }

    private int checkDifficulty(Integer difficulty, List<FieldErrorDetail> errors) {
        int value = difficulty == null ? 0 : difficulty;
        if (!stageCatalog.hasDifficulty(value)) {
            // 받는 난이도는 master/difficulties.json(M9). 오류를 모은 뒤 한 번에 400을 내므로 자리 값은 0
            errors.add(new FieldErrorDetail("difficulty", "받는 난이도가 아닙니다: " + value));
            return 0;
        }
        return value;
    }

    private static RankingPeriod checkPeriod(String period, List<FieldErrorDetail> errors) {
        return RankingPeriod.parse(period).orElseGet(() -> {
            errors.add(new FieldErrorDetail("period", "all 또는 weekly여야 합니다."));
            return RankingPeriod.ALL;
        });
    }

    private Map<Long, String> nicknames(List<RankingRow> rows) {
        Set<Long> ids = new HashSet<>();
        rows.forEach(row -> ids.add(row.accountId()));
        return ids.isEmpty() ? Map.of() : accountService.getNicknames(ids);
    }

}
