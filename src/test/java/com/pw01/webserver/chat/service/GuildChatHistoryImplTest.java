package com.pw01.webserver.chat.service;

import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.common.chat.ChatHistoryPage;
import com.pw01.webserver.common.chat.ChatMessage;
import com.pw01.webserver.common.chat.ChatScope;
import com.pw01.webserver.common.chat.GuildChatHistory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 길드 채팅 저장·내역(GuildChatHistoryImpl, V11 guild_chat_message)을 실제 MySQL(테스트 컨테이너)로 확인한다.
 * 약속(common/chat/GuildChatHistory)과 ChatContractTest의 가짜 구현이 보이는 기대(최신부터·커서 줄 빼고·보관 기간)를 진짜 표로 다시 본다.
 * 실패하면 저장소 질의(번호 < before, created_at ≥ 지금 − 3일, 번호 역순, size + 1), V11 칼럼, 길드 ID 문자열 변환을 의심한다.
 * 테스트끼리 같은 DB를 쓰므로 길드 ID를 서로 다르게 쓴다(92001~). 길드 ID는 실제와 같은 10진수 문자열(Sang Q-2 ③).
 */
@IntegrationTest
class GuildChatHistoryImplTest {

    @Autowired
    GuildChatHistory history;

    @Autowired
    JdbcTemplate jdbc;

    // 확인: 저장하면 번호(10진수 문자열)·길드·보낸 사람·닉네임·글·시각(밀리초까지)이 채워진 GUILD 메시지가 돌아온다
    @Test
    void 저장하면_번호와_시각을_매긴다() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        ChatMessage saved = history.append("92001", 11L, "용사", "안녕");

        assertThat(saved.scope()).isEqualTo(ChatScope.GUILD);
        assertThat(saved.messageId()).matches("\\d+");
        assertThat(saved.guildId()).isEqualTo("92001");
        assertThat(saved.senderAccountId()).isEqualTo("11");
        assertThat(saved.senderNickname()).isEqualTo("용사");
        assertThat(saved.text()).isEqualTo("안녕");
        assertThat(saved.sentAt()).isBetween(before, Instant.now().plusMillis(1));
        assertThat(saved.sentAt().getNano() % 1_000_000).isZero();
    }

    // 확인: 최신부터 size개(새것 → 옛것), nextCursor로 다음 묶음, 커서 줄은 빼고, 마지막 묶음의 nextCursor는 null, 다른 길드는 안 섞임
    @Test
    void 최신부터_커서로_불러온다() {
        for (int i = 1; i <= 5; i++) {
            history.append("92002", 1L, "a", "m" + i);
        }
        history.append("92003", 2L, "b", "other guild");

        ChatHistoryPage first = history.loadLatest("92002", null, 2);
        ChatHistoryPage second = history.loadLatest("92002", first.nextCursor(), 2);
        ChatHistoryPage last = history.loadLatest("92002", second.nextCursor(), 2);

        assertThat(first.messages()).extracting(ChatMessage::text).containsExactly("m5", "m4");
        assertThat(second.messages()).extracting(ChatMessage::text).containsExactly("m3", "m2");
        assertThat(last.messages()).extracting(ChatMessage::text).containsExactly("m1");
        assertThat(last.nextCursor()).isNull();
        assertThat(first.nextCursor()).isEqualTo(first.messages().getLast().messageId());
    }

    // 확인: 딱 size개만 있으면 다음 묶음이 없다(size + 1로 읽어 판단)
    @Test
    void 딱_맞으면_다음_커서가_없다() {
        history.append("92004", 1L, "a", "x1");
        history.append("92004", 1L, "a", "x2");

        ChatHistoryPage page = history.loadLatest("92004", null, 2);

        assertThat(page.messages()).hasSize(2);
        assertThat(page.nextCursor()).isNull();
    }

    // 확인: 보관 기간(3일) 경계 — 2일 23시간 전 줄은 보이고, 3일 1분 전 줄은 안 보인다. 실패하면 retention 값·created_at 비교를 의심
    @Test
    void 보관_기간_밖은_돌려주지_않는다() {
        Instant now = Instant.now();
        insertAt(92005L, "old", now.minus(Duration.ofDays(3)).minus(Duration.ofMinutes(1)));
        insertAt(92005L, "recent", now.minus(Duration.ofDays(3)).plus(Duration.ofHours(1)));
        history.append("92005", 1L, "a", "now");

        assertThat(history.loadLatest("92005", null, 10).messages())
                .extracting(ChatMessage::text).containsExactly("now", "recent");
    }

    // 확인: 숫자가 아닌 길드 ID — 불러오기는 빈 묶음(그런 길드 없음), 저장은 예외(부르는 쪽 실수). before가 숫자가 아니면 예외
    @Test
    void 숫자가_아닌_ID() {
        assertThat(history.loadLatest("abc", null, 10).messages()).isEmpty();
        assertThat(history.loadLatest("٣", null, 10).messages()).isEmpty();
        assertThatThrownBy(() -> history.append("7a", 1L, "a", "t")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> history.loadLatest("92006", "x", 10)).isInstanceOf(IllegalArgumentException.class);
    }

    private void insertAt(Long guildId, String text, Instant at) {
        Timestamp time = Timestamp.from(at.truncatedTo(ChronoUnit.MILLIS));
        jdbc.update("""
                INSERT INTO guild_chat_message (guild_id, sender_account_id, sender_nickname, text, created_at, updated_at)
                VALUES (?, 1, 'a', ?, ?, ?)
                """, guildId, text, time, time);
    }

}
