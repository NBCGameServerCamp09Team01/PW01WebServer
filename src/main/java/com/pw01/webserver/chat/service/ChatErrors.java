package com.pw01.webserver.chat.service;

import com.pw01.webserver.common.error.ErrorResponse.FieldErrorDetail;
import com.pw01.webserver.common.error.ForbiddenException;
import com.pw01.webserver.common.error.InvalidRequestException;
import com.pw01.webserver.common.error.TooManyRequestsException;

import java.util.List;

/**
 * 채팅 오류 코드와 예외(루트 docs/contracts/chat-api.md "오류", M23).
 * 오류마다 HTTP 상태(예외 클래스)를 이 파일에서만 정한다(StatErrors·GuildErrors와 같은 결).
 * GUILD_NOT_MEMBER는 길드 명세와 글자만 같게 두고 상수는 여기에 둔다 — 채팅이 길드 패키지에 기대지 않게(채팅 지시서 3장).
 */
public final class ChatErrors {

    public static final String CHAT_TEXT_INVALID = "CHAT_TEXT_INVALID";
    public static final String CHAT_RATE_LIMITED = "CHAT_RATE_LIMITED";
    public static final String CHAT_MUTED = "CHAT_MUTED";
    public static final String GUILD_NOT_MEMBER = "GUILD_NOT_MEMBER";

    private ChatErrors() {
    }

    /** 400: 빈 글·너무 긺·줄바꿈·제어 문자. errors[].field = "text" */
    public static InvalidRequestException textInvalid(String reason) {
        return new InvalidRequestException(CHAT_TEXT_INVALID, "채팅 글이 올바르지 않습니다.",
                List.of(new FieldErrorDetail("text", reason)));
    }

    /** 429: 짧은 시간에 너무 많이 보냄. retryAfterSeconds 뒤에 다시 보낼 수 있다 */
    public static TooManyRequestsException rateLimited(long retryAfterSeconds) {
        return new TooManyRequestsException(CHAT_RATE_LIMITED, "채팅을 너무 빨리 보내고 있습니다. 잠시 뒤에 보내 주세요.",
                retryAfterSeconds);
    }

    /** 429: 횟수 제한에 여러 번 걸려 잠깐 채팅을 쉼(도배 쉬기). retryAfterSeconds = 남은 쉬기 시간 */
    public static TooManyRequestsException muted(long retryAfterSeconds) {
        return new TooManyRequestsException(CHAT_MUTED, "도배로 잠시 채팅을 쉽니다.", retryAfterSeconds);
    }

    /** 403: 길드 없이 길드 채팅을 보내거나 불러옴 */
    public static ForbiddenException notGuildMember() {
        return new ForbiddenException(GUILD_NOT_MEMBER, "길드에 가입한 사람만 길드 채팅을 쓸 수 있습니다.");
    }

}
