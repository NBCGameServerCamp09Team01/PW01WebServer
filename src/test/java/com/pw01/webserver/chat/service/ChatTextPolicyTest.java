package com.pw01.webserver.chat.service;

import com.pw01.webserver.common.error.InvalidRequestException;
import com.pw01.webserver.config.ChatProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 채팅 글 규칙 단위 테스트(컨테이너 없음, 루트 docs/contracts/chat-api.md "글 규칙", 채팅 계획서 C-2).
 * 실패하면 ChatTextPolicy의 순서(자르기 → 빈 글 → 제어 문자 → 길이)와 길이를 코드 포인트로 세는지를 의심한다.
 */
class ChatTextPolicyTest {

    private final ChatTextPolicy policy = new ChatTextPolicy(properties(100));

    // 확인: 앞뒤 공백(전각 공백 포함)을 자른 글을 돌려주고, 가운데 공백은 그대로 둔다
    @Test
    void 앞뒤_공백만_자른다() {
        assertThat(policy.check("  안녕  하세요 　")).isEqualTo("안녕  하세요");
    }

    // 확인: null·빈 글·공백만 → 400 CHAT_TEXT_INVALID, errors[].field = text
    @Test
    void 빈_글은_거절() {
        for (String text : new String[]{null, "", "   ", "　"}) {
            assertThatThrownBy(() -> policy.check(text))
                    .isInstanceOfSatisfying(InvalidRequestException.class, e -> {
                        assertThat(e.getCode()).isEqualTo(ChatErrors.CHAT_TEXT_INVALID);
                        assertThat(e.getErrors()).extracting("field").containsExactly("text");
                    });
        }
    }

    // 확인: 100자까지 통과, 101자는 거절(경계)
    @Test
    void 길이_경계() {
        assertThat(policy.check("가".repeat(100))).hasSize(100);
        assertThatThrownBy(() -> policy.check("가".repeat(101)))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ChatErrors.CHAT_TEXT_INVALID));
    }

    // 확인: 이모지는 코드 포인트 하나로 센다 — length()로 세면 100개가 200이 되어 잘못 거절된다
    @Test
    void 이모지는_한_글자로_센다() {
        String hundredEmoji = "😀".repeat(100);
        assertThat(hundredEmoji.length()).isEqualTo(200);
        assertThat(policy.check(hundredEmoji)).isEqualTo(hundredEmoji);
        assertThatThrownBy(() -> policy.check("😀".repeat(101)))
                .isInstanceOf(InvalidRequestException.class);
    }

    // 확인: 줄바꿈·탭·NUL 같은 제어 문자가 가운데 있으면 거절(앞뒤의 줄바꿈은 공백처럼 잘림)
    @Test
    void 제어_문자는_거절() {
        for (String text : new String[]{"a\nb", "a\rb", "a\tb", "a\u0000b", "a\u007Fb"}) {
            assertThatThrownBy(() -> policy.check(text))
                    .as(text.codePoints().mapToObj(Integer::toHexString).toList().toString())
                    .isInstanceOf(InvalidRequestException.class);
        }
        assertThat(policy.check("\n안녕\n")).isEqualTo("안녕");
    }

    // 확인: 설정 값이 틀리면 서버가 뜨지 않는다 — 글 길이는 표 칼럼(100)보다 길 수 없음
    @Test
    void 설정_값_검사() {
        assertThatThrownBy(() -> properties(101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatProperties.Guild(Duration.ofDays(3), 10, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatProperties.Rate(Duration.ZERO, 5)).isInstanceOf(IllegalArgumentException.class);
    }

    private static ChatProperties properties(int maxLength) {
        return new ChatProperties(maxLength,
                new ChatProperties.Rate(Duration.ofSeconds(10), 5),
                new ChatProperties.Mute(3, Duration.ofSeconds(60), Duration.ofSeconds(60)),
                new ChatProperties.Guild(Duration.ofDays(3), 10, 50),
                new ChatProperties.Cleanup(true, "0 30 3 * * *", 1000),
                new ChatProperties.Recent(true, 10));
    }

}
