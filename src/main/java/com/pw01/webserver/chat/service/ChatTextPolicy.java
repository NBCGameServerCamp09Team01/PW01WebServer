package com.pw01.webserver.chat.service;

import com.pw01.webserver.config.ChatProperties;
import org.springframework.stereotype.Component;

/**
 * 채팅 글 규칙(루트 docs/contracts/chat-api.md "글 규칙", 채팅 계획서 C-2).
 * 앞뒤 공백을 자른 글을 돌려주고, 규칙에 어긋나면 400 CHAT_TEXT_INVALID를 던진다. 서버는 글을 몰래 바꾸지 않는다(자르기 말고는 그대로).
 * <ul>
 *   <li>길이는 코드 포인트로 센다. String.length()는 이모지 하나를 2로 세서 게임(UE)과 경계가 어긋난다</li>
 *   <li>줄바꿈·탭·NUL 같은 제어 문자는 거절한다(한 줄 채팅)</li>
 * </ul>
 */
@Component
public class ChatTextPolicy {

    private final int maxLength;

    public ChatTextPolicy(ChatProperties properties) {
        this.maxLength = properties.maxLength();
    }

    /**
     * @param text 요청의 글(null이면 빈 글로 본다)
     * @return 앞뒤 공백을 자른 글
     */
    public String check(String text) {
        String stripped = text == null ? "" : text.strip();
        if (stripped.isEmpty()) {
            throw ChatErrors.textInvalid("글을 입력해 주세요.");
        }
        if (stripped.codePoints().anyMatch(Character::isISOControl)) {
            throw ChatErrors.textInvalid("줄바꿈이나 제어 문자는 쓸 수 없습니다.");
        }
        int length = stripped.codePointCount(0, stripped.length());
        if (length > maxLength) {
            throw ChatErrors.textInvalid(maxLength + "자까지 쓸 수 있습니다(지금 " + length + "자).");
        }
        return stripped;
    }

}
