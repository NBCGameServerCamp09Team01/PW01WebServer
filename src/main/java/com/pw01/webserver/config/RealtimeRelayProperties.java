package com.pw01.webserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 실시간 서버 사이 전달 설정 값(pw01.realtime.relay.*). 기본값은 application.properties에 있다.
 * 값의 뜻은 루트 docs/contracts/redis-keys.md "실시간 서버 사이 전달"·realtime-api.md "서버 여러 대" 절이 기준이다.
 *
 * @param enabled            false면 서버 한 대처럼 이 서버 연결에만 보낸다(발행·구독 안 함). 되돌릴 때 쓰는 스위치
 * @param globalChannel      전체 대상 메시지 채널
 * @param guildChannelPrefix 길드마다 채널의 앞부분. 뒤에 길드 ID를 붙이고, 서버는 앞부분 + "*" 패턴 하나로 모두 듣는다
 * @param targetChannel      계정 지정 메시지·세션 다시 확인 채널
 * @param maxTargets         한 번에 지정할 수 있는 계정 수. 넘으면 보내지 않는다
 * @param maxMessageBytes    게임에 갈 메시지 JSON의 최대 크기(바이트). 넘으면 보내지 않는다
 */
@ConfigurationProperties("pw01.realtime.relay")
public record RealtimeRelayProperties(boolean enabled, String globalChannel, String guildChannelPrefix,
                                      String targetChannel, int maxTargets, int maxMessageBytes) {

    public RealtimeRelayProperties {
        requireText(globalChannel, "global-channel");
        requireText(guildChannelPrefix, "guild-channel-prefix");
        requireText(targetChannel, "target-channel");
        if (maxTargets < 1) {
            throw new IllegalArgumentException("pw01.realtime.relay.max-targets는 1 이상이어야 합니다: " + maxTargets);
        }
        if (maxMessageBytes < 256) {
            throw new IllegalArgumentException("pw01.realtime.relay.max-message-bytes는 256 이상이어야 합니다: " + maxMessageBytes);
        }
    }

    /** 이 채널 이름이 길드 채널이면 길드 ID, 아니면 null */
    public String guildIdOf(String channel) {
        return channel.startsWith(guildChannelPrefix) ? channel.substring(guildChannelPrefix.length()) : null;
    }

    public String guildChannel(String guildId) {
        return guildChannelPrefix + guildId;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("pw01.realtime.relay." + name + "이(가) 비어 있습니다");
        }
    }

}
