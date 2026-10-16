package com.pw01.webserver.chat.service;

import com.pw01.webserver.config.ChatProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;

/**
 * 길드 채팅 보관 지우기(M23, 루트 docs/flows/chat.md ⑧): 하루 한 번(pw01.chat.cleanup.cron, UTC) 보관 기간(3일)보다 오래된 줄을 지운다.
 * 지우기는 GuildChatHistoryImpl.deleteExpired(새 트랜잭션, batch-size씩 반복). 서버 여러 대가 같이 돌아도 결과가 같다(잠금 없음).
 * 내역은 늘 보관 기간 안만 읽으므로 지우기가 늦거나 실패해도 게임에 보이지 않는다. 실패하면 다음 날 다시.
 * 앱 전체의 @EnableScheduling을 켜지 않고 전용 스케줄러를 쓴다(StatTotalReconciler와 같은 이유).
 */
@Service
public class GuildChatCleanupJob implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(GuildChatCleanupJob.class);

    private final GuildChatHistoryImpl guildChatHistory;
    private final ChatProperties.Cleanup cleanup;
    private ThreadPoolTaskScheduler scheduler;

    public GuildChatCleanupJob(GuildChatHistoryImpl guildChatHistory, ChatProperties properties) {
        this.guildChatHistory = guildChatHistory;
        this.cleanup = properties.cleanup();
    }

    @Override
    public void afterPropertiesSet() {
        if (!cleanup.enabled()) {
            log.info("길드 채팅 보관 지우기 꺼짐(pw01.chat.cleanup.enabled=false)");
            return;
        }
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("guild-chat-cleanup-");
        scheduler.initialize();
        scheduler.schedule(this::runSafely, new CronTrigger(cleanup.cron(), ZoneOffset.UTC));
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /** 한 번 돌린다. 지운 줄 수를 돌려준다(시험이 직접 부른다) */
    public int runOnce() {
        int deleted = guildChatHistory.deleteExpired();
        log.info("길드 채팅 보관 지우기: {}줄 지움", deleted);
        return deleted;
    }

    private void runSafely() {
        try {
            runOnce();
        } catch (Exception e) {
            log.error("길드 채팅 보관 지우기 실패(다음 주기에 다시)", e);
        }
    }

}
