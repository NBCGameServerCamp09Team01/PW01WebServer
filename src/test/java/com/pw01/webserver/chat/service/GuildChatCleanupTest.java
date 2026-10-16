package com.pw01.webserver.chat.service;

import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.common.chat.GuildDisbandedEvent;
import com.pw01.webserver.common.error.ApiException;
import com.pw01.webserver.guild.GuildTestAccounts;
import com.pw01.webserver.guild.dto.GuildActionRequest;
import com.pw01.webserver.guild.service.GuildCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 길드 채팅 지우기 두 길(루트 docs/flows/chat.md ⑦ 해체·⑧ 보관)을 실제 MySQL로 확인한다.
 * 해체: GuildDisbandedEvent를 트랜잭션 안에서 내고 커밋·롤백에 따라 줄이 지워지는지(ChatDisbandListener, AFTER_COMMIT + REQUIRES_NEW).
 * 보관: GuildChatCleanupJob.runOnce가 3일 지난 줄만 지우는지. 묶음 반복(1000·1000·500)은 GuildChatHistoryImplDeleteUnitTest가 본다.
 * 실패하면 ChatDisbandListener의 phase, GuildChatHistoryImpl의 REQUIRES_NEW·deleteInBatches 끝 조건, DELETE … LIMIT 쿼리를 먼저 의심한다.
 * 다른 시험과 겹치지 않게 길드 ID 94001~ (채팅 표는 외래 키가 없어 길드 행이 없어도 됨).
 */
@IntegrationTest
class GuildChatCleanupTest {

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    GuildChatCleanupJob cleanupJob;

    @Autowired
    GuildCommandService guildCommands;

    @Autowired
    JdbcTemplate jdbc;

    // 확인: 해체 이벤트가 든 트랜잭션이 커밋되면 그 길드 줄만 모두 지워짐(다른 길드 줄은 남음)
    @Test
    void 해체가_커밋되면_그_길드만_지움() {
        insertMessages(94001L, 3, Duration.ZERO);
        insertMessages(94002L, 2, Duration.ZERO);

        transactionTemplate.executeWithoutResult(
                status -> events.publishEvent(new GuildDisbandedEvent("94001", List.of())));

        assertThat(count(94001L)).isZero();
        assertThat(count(94002L)).isEqualTo(2);
    }

    // 확인: 해체가 롤백되면 이벤트를 받지 않아 줄이 남음. 트랜잭션 밖에서 낸 이벤트도 받지 않음(소셜은 늘 트랜잭션 안에서 냄)
    @Test
    void 롤백되면_지우지_않음() {
        insertMessages(94003L, 3, Duration.ZERO);

        transactionTemplate.executeWithoutResult(status -> {
            events.publishEvent(new GuildDisbandedEvent("94003", List.of()));
            status.setRollbackOnly();
        });
        events.publishEvent(new GuildDisbandedEvent("94003", List.of()));

        assertThat(count(94003L)).isEqualTo(3);
    }

    // 확인: 묶음 크기(1000)보다 많은 줄도 끝까지 지움. 숫자가 아닌 길드 ID는 넘어감(예외 없음)
    @Test
    void 많은_줄도_모두_지움() {
        insertMessages(94004L, 2100, Duration.ZERO);

        transactionTemplate.executeWithoutResult(status -> {
            events.publishEvent(new GuildDisbandedEvent("94004", List.of()));
            events.publishEvent(new GuildDisbandedEvent("not-a-number", List.of()));
        });

        assertThat(count(94004L)).isZero();
    }

    // 확인: 진짜 해체(GuildCommandService.disband) 뒤 그 길드 채팅이 지워짐. 길드장이 아니라 거절되면(롤백) 남음
    @Test
    void 실제_해체_뒤_지움() {
        long leader = GuildTestAccounts.account(jdbc);
        long member = GuildTestAccounts.account(jdbc);
        long guildId = GuildTestAccounts.guildWithLeader(jdbc, "채팅해체" + GuildTestAccounts.suffix(), leader, 2, 30);
        jdbc.update("""
                INSERT INTO account_guild (account_id, guild_id, role, joined_at, version, created_at, updated_at)
                VALUES (?, ?, 'MEMBER', NOW(3), 0, NOW(3), NOW(3))""", member, guildId);
        insertMessages(guildId, 4, Duration.ZERO);
        jdbc.update("UPDATE guild_counter SET guild_count = 1 WHERE counter_id = 1");

        assertThatThrownBy(() -> guildCommands.disband(member, action())).isInstanceOf(ApiException.class);
        assertThat(count(guildId)).isEqualTo(4);

        guildCommands.disband(leader, action());
        assertThat(count(guildId)).isZero();
    }

    // 확인: 보관 지우기는 3일 지난 줄만 지움(3일이 조금 안 된 줄은 남음), 묶음보다 많아도 모두. 다시 돌리면 0줄
    @Test
    void 보관_기간이_지난_줄만_지움() {
        cleanupJob.runOnce(); // 다른 시험이 넣은 옛 줄을 먼저 치움
        insertMessages(94010L, 2500, Duration.ofDays(3).plusMinutes(1));
        insertMessages(94010L, 2, Duration.ofDays(3).minusHours(1));
        insertMessages(94011L, 1, Duration.ZERO);

        assertThat(cleanupJob.runOnce()).isEqualTo(2500);
        assertThat(count(94010L)).isEqualTo(2);
        assertThat(count(94011L)).isEqualTo(1);
        assertThat(cleanupJob.runOnce()).isZero();
    }

    /** 길드 채팅 n줄을 age만큼 옛날 시각으로 */
    private void insertMessages(long guildId, int n, Duration age) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            rows.add(new Object[]{guildId, "줄 " + i, age.toSeconds(), age.toSeconds()});
        }
        jdbc.batchUpdate("""
                INSERT INTO guild_chat_message (guild_id, sender_account_id, sender_nickname, text, created_at, updated_at)
                VALUES (?, 1, '보낸이', ?, NOW(3) - INTERVAL ? SECOND, NOW(3) - INTERVAL ? SECOND)""", rows);
    }

    private int count(long guildId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM guild_chat_message WHERE guild_id = ?", Integer.class, guildId);
    }

    private static GuildActionRequest action() {
        return new GuildActionRequest(UUID.randomUUID().toString());
    }

}
