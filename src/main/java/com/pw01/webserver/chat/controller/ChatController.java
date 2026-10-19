package com.pw01.webserver.chat.controller;

import com.pw01.webserver.auth.interceptor.LoginAccount;
import com.pw01.webserver.chat.dto.ChatHistoryResponse;
import com.pw01.webserver.chat.dto.ChatMessageResponse;
import com.pw01.webserver.chat.dto.ChatSendRequest;
import com.pw01.webserver.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 채팅(루트 docs/contracts/chat-api.md CH1·CH2·CH3, M23). 보낼 길드는 요청에 없고 서비스가 로그인한 계정의 길드로 정한다.
 * 받기는 실시간 연결의 CHAT_MESSAGE다(이 컨트롤러가 아님). 전체 내역 GET /chats/global은 이름만 예약(열지 않음).
 */
@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** CH1 전체 채팅 보내기 */
    @PostMapping("/global")
    public ChatMessageResponse sendGlobal(@LoginAccount Long accountId, @Valid @RequestBody ChatSendRequest request) {
        return chatService.sendGlobal(accountId, request);
    }

    /** CH2 내 길드에 채팅 보내기 */
    @PostMapping("/guild")
    public ChatMessageResponse sendGuild(@LoginAccount Long accountId, @Valid @RequestBody ChatSendRequest request) {
        return chatService.sendGuild(accountId, request);
    }

    /** CH3 내 길드 채팅 내역(최신부터 커서) */
    @GetMapping("/guild")
    public ChatHistoryResponse guildHistory(@LoginAccount Long accountId,
                                            @RequestParam(required = false) String before,
                                            @RequestParam(required = false) Integer size) {
        return chatService.guildHistory(accountId, before, size);
    }

}
