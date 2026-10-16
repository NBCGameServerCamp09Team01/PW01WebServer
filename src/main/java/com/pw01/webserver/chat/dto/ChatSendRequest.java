package com.pw01.webserver.chat.dto;

import com.pw01.webserver.stageplay.dto.StagePlayIds;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * CH1 전체·CH2 길드 채팅 보내기 요청(루트 docs/contracts/chat-api.md). 형식만 여기서 본다.
 * 글 규칙(빈 글·길이·제어 문자 → CHAT_TEXT_INVALID)은 ChatTextPolicy가 본다 — 빈 글도 VALIDATION_FAILED가 아니라 CHAT_TEXT_INVALID라서 text는 @NotNull만.
 */
public record ChatSendRequest(

        @NotBlank(message = "requestId가 필요합니다.")
        @Pattern(regexp = StagePlayIds.UUID_PATTERN, message = "requestId는 UUID(하이픈 36자)여야 합니다.")
        String requestId,

        @NotNull(message = "text가 필요합니다.")
        String text
) {
}
