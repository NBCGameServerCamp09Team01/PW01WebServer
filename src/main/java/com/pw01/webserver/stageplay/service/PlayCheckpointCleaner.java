package com.pw01.webserver.stageplay.service;

import com.pw01.webserver.stageplay.repository.PlayCheckpointRedisStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 플레이가 끝나면(결과 저장·포기) 커밋 뒤에 체크포인트를 지운다(루트 docs/contracts/redis-keys.md "플레이 체크포인트").
 * 지우기에 실패해도 결과·포기 응답은 바꾸지 않는다. 남은 값은 플레이 마감 때 Redis 만료로 사라지고,
 * 그 사이에도 C2는 진행 중이 아닌 플레이에 204를 돌려주므로 게임에 나가지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PlayCheckpointCleaner {

    private static final Logger log = LoggerFactory.getLogger(PlayCheckpointCleaner.class);

    private final PlayCheckpointRedisStore checkpointStore;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStagePlayEnded(StagePlayEndedEvent event) {
        try {
            checkpointStore.delete(event.stagePlayId());
        } catch (RuntimeException e) {
            log.warn("플레이 체크포인트를 지우지 못함(마감 때 만료로 사라짐) stagePlayId={} cause={}",
                    event.stagePlayId(), e.getMessage());
        }
    }

}
