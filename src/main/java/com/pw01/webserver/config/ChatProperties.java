package com.pw01.webserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 채팅 설정 값(pw01.chat.*, M23). 기본값은 application.properties에 있다. 틀린 값이면 서버가 뜨지 않는다.
 * 값의 뜻은 루트 docs/contracts/chat-api.md "규칙"·redis-keys.md "채팅"이 기준이다.
 *
 * @param maxLength 채팅 글 최대 길이(코드 포인트 수 — 이모지 하나가 1). 길드 채팅 표 text 칼럼(VARCHAR)보다 길면 안 된다
 * @param rate      횟수 제한(전체·길드 함께 센다)
 * @param mute      도배 쉬기(횟수 제한에 여러 번 걸리면 잠깐 막음)
 * @param guild     길드 채팅 보관·내역 묶음
 * @param cleanup   보관 기간이 지난 길드 채팅 지우기(전용 스케줄러)
 * @param recent    길드 채팅 최근 묶음 캐시(Lv2)
 */
@ConfigurationProperties("pw01.chat")
public record ChatProperties(int maxLength, Rate rate, Mute mute, Guild guild, Cleanup cleanup, Recent recent) {

    /** V11 guild_chat_message.text 칼럼 길이. maxLength가 이보다 크면 저장에서 잘리거나 실패한다 */
    public static final int TEXT_COLUMN_LENGTH = 100;

    public ChatProperties {
        if (maxLength < 1 || maxLength > TEXT_COLUMN_LENGTH) {
            throw new IllegalArgumentException("pw01.chat.max-length는 1~" + TEXT_COLUMN_LENGTH + "이어야 합니다: " + maxLength);
        }
        if (rate == null || mute == null || guild == null || cleanup == null || recent == null) {
            throw new IllegalArgumentException("pw01.chat.rate·mute·guild·cleanup·recent 중 비어 있는 것이 있습니다");
        }
    }

    /**
     * @param window 세는 시간(고정 창). 1밀리초 이상
     * @param max    창 안에서 보낼 수 있는 수
     */
    public record Rate(Duration window, int max) {

        public Rate {
            requirePositive("pw01.chat.rate.window", window);
            if (max < 1) {
                throw new IllegalArgumentException("pw01.chat.rate.max는 1 이상이어야 합니다: " + max);
            }
        }

    }

    /**
     * @param strikes      쉬기에 들어가는 경고 수(횟수 제한에 처음 걸린 창마다 경고 1)
     * @param strikeWindow 경고를 세는 시간
     * @param duration     쉬는 시간(전체·길드 모두 막음)
     */
    public record Mute(int strikes, Duration strikeWindow, Duration duration) {

        public Mute {
            if (strikes < 1) {
                throw new IllegalArgumentException("pw01.chat.mute.strikes는 1 이상이어야 합니다: " + strikes);
            }
            requirePositive("pw01.chat.mute.strike-window", strikeWindow);
            requirePositive("pw01.chat.mute.duration", duration);
        }

    }

    /**
     * @param retention   보관 기간. 내역은 이보다 오래된 줄을 돌려주지 않는다
     * @param pageSize    size를 안 보냈을 때
     * @param pageSizeMax size 최댓값(넘으면 400)
     */
    public record Guild(Duration retention, int pageSize, int pageSizeMax) {

        public Guild {
            requirePositive("pw01.chat.guild.retention", retention);
            if (pageSize < 1 || pageSizeMax < pageSize) {
                throw new IllegalArgumentException(
                        "pw01.chat.guild는 1 ≤ page-size ≤ page-size-max여야 합니다: " + pageSize + ", " + pageSizeMax);
            }
        }

    }

    /**
     * @param enabled   지우기를 돌릴지
     * @param cron      돌리는 때(초 분 시 일 월 요일, UTC)
     * @param batchSize 한 번에 지우는 줄 수(짧은 트랜잭션을 0줄이 될 때까지 반복)
     */
    public record Cleanup(boolean enabled, String cron, int batchSize) {

        public Cleanup {
            if (cron == null || cron.isBlank()) {
                throw new IllegalArgumentException("pw01.chat.cleanup.cron이 비어 있습니다");
            }
            if (batchSize < 1) {
                throw new IllegalArgumentException("pw01.chat.cleanup.batch-size는 1 이상이어야 합니다: " + batchSize);
            }
        }

    }

    /**
     * @param enabled 캐시를 쓸지
     * @param size    캐시에 두는 최신 줄 수(내역 첫 묶음)
     */
    public record Recent(boolean enabled, int size) {

        public Recent {
            if (size < 1) {
                throw new IllegalArgumentException("pw01.chat.recent.size는 1 이상이어야 합니다: " + size);
            }
        }

    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + "는 0보다 커야 합니다: " + value);
        }
    }

}
