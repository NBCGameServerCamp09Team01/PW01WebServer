package com.pw01.webserver.stageplay.dto;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** 스테이지 플레이 ID·requestId 모양 규칙(UUID 하이픈 36자). 대소문자 모두 받고 서버는 소문자로 맞춘다 */
public final class StagePlayIds {

    public static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private static final Pattern UUID = Pattern.compile(UUID_PATTERN);

    private StagePlayIds() {
    }

    /** UUID 모양이면 소문자로, 아니면 빈 값 */
    public static Optional<String> normalize(String value) {
        if (value == null || !UUID.matcher(value).matches()) {
            return Optional.empty();
        }
        return Optional.of(value.toLowerCase(Locale.ROOT));
    }

}
