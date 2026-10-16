package com.pw01.webserver.guild.service;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * API·계약(GuildDirectory)에서 문자열로 오가는 길드 ID(guild.guild_id, BIGINT)를 숫자로 바꾼다.
 * 숫자가 아닌 값은 예외 대신 빈 값으로 돌려준다. API는 빈 값을 400으로, GuildDirectory는 "그런 길드 없음"으로 본다.
 */
public final class GuildIds {

    /** 양의 정수, 앞에 0 없음, 18자리까지(Long 범위 안) */
    private static final Pattern ID = Pattern.compile("[1-9][0-9]{0,17}");

    private GuildIds() {
    }

    public static Optional<Long> parse(String value) {
        if (value == null || !ID.matcher(value).matches()) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(value));
    }

}
