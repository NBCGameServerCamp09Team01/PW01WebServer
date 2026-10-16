package com.pw01.webserver.chat.service;

import com.pw01.webserver.account.service.AccountService;
import com.pw01.webserver.chat.dto.ChatHistoryResponse;
import com.pw01.webserver.chat.dto.ChatMessageResponse;
import com.pw01.webserver.chat.dto.ChatSendKey;
import com.pw01.webserver.chat.dto.ChatSendRequest;
import com.pw01.webserver.chat.repository.ChatRateDecision;
import com.pw01.webserver.chat.repository.RedisChatRateLimiter;
import com.pw01.webserver.common.chat.ChatMessage;
import com.pw01.webserver.common.chat.ChatScope;
import com.pw01.webserver.common.chat.GlobalChatHistory;
import com.pw01.webserver.common.chat.GuildChatHistory;
import com.pw01.webserver.common.chat.GuildDirectory;
import com.pw01.webserver.common.idempotency.IdempotencyService;
import com.pw01.webserver.config.ChatProperties;
import com.pw01.webserver.realtime.dto.RealtimeMessage;
import com.pw01.webserver.realtime.dto.RealtimeMessageType;
import com.pw01.webserver.realtime.service.RealtimeNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 채팅 보내기·길드 내역(루트 docs/contracts/chat-api.md CH1·CH2·CH3, M23).
 * 보내기 판정 순서(명세 "판정 순서"): 글 규칙 → 한 번만 반영(requestId) → 도배 쉬기·횟수 제한(chat_rate.lua 한 번) → (길드) 길드원인가 → 만들어 보냄.
 * 횟수 세기가 멱등 안이라, 같은 requestId 재전송(처음 응답 돌려주기)은 수를 세지 않는다. 거절(4xx)은 기억하지 않아 다시 보내면 다시 판정한다.
 * 길드 채팅은 저장 트랜잭션이 커밋된 뒤에 그 길드원에게만 보낸다(저장 실패면 아무에게도 안 감). 전체 채팅은 저장하지 않고 바로 모두에게.
 * 로그에 채팅 글은 남기지 않는다(번호·계정·길드만).
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatTextPolicy textPolicy;
    private final IdempotencyService idempotencyService;
    private final RedisChatRateLimiter rateLimiter;
    private final GuildDirectory guildDirectory;
    private final GuildChatHistory guildChatHistory;
    private final GlobalChatHistory globalChatHistory;
    private final AccountService accountService;
    private final RealtimeNotifier realtimeNotifier;
    private final TransactionTemplate transactionTemplate;
    private final ChatProperties.Guild guild;

    public ChatService(ChatTextPolicy textPolicy, IdempotencyService idempotencyService,
                       RedisChatRateLimiter rateLimiter, GuildDirectory guildDirectory,
                       GuildChatHistory guildChatHistory, GlobalChatHistory globalChatHistory,
                       AccountService accountService, RealtimeNotifier realtimeNotifier,
                       TransactionTemplate transactionTemplate, ChatProperties properties) {
        this.textPolicy = textPolicy;
        this.idempotencyService = idempotencyService;
        this.rateLimiter = rateLimiter;
        this.guildDirectory = guildDirectory;
        this.guildChatHistory = guildChatHistory;
        this.globalChatHistory = globalChatHistory;
        this.accountService = accountService;
        this.realtimeNotifier = realtimeNotifier;
        this.transactionTemplate = transactionTemplate;
        this.guild = properties.guild();
    }

    /** CH1 전체 채팅 보내기: 저장하지 않고(GlobalChatHistory가 번호·시각만 매김) 접속한 모두에게 */
    public ChatMessageResponse sendGlobal(Long accountId, ChatSendRequest request) {
        String text = textPolicy.check(request.text());
        return runOnce(accountId, request, ChatScope.GLOBAL, () -> {
            acquireRate(accountId);
            ChatMessage message = globalChatHistory.append(accountId, nicknameOf(accountId), text);
            realtimeNotifier.sendToAll(RealtimeMessage.of(RealtimeMessageType.CHAT_MESSAGE, message.toRealtimeData()));
            log.info("전체 채팅 보냄 messageId={} accountId={}", message.messageId(), accountId);
            return ChatMessageResponse.from(message);
        });
    }

    /** CH2 길드 채팅 보내기: 횟수 제한을 먼저 세고(길드 없어도 셈, 명세) 길드원인지 본 뒤 저장 → 커밋 뒤 그 길드원에게 */
    public ChatMessageResponse sendGuild(Long accountId, ChatSendRequest request) {
        String text = textPolicy.check(request.text());
        return runOnce(accountId, request, ChatScope.GUILD, () -> {
            acquireRate(accountId);
            String guildId = guildDirectory.guildIdOf(accountId).orElseThrow(ChatErrors::notGuildMember);
            String nickname = nicknameOf(accountId);
            ChatMessage message = transactionTemplate.execute(
                    status -> guildChatHistory.append(guildId, accountId, nickname, text));
            // 여기는 커밋이 끝난 뒤다(TransactionTemplate가 돌아옴 = 커밋됨)
            realtimeNotifier.sendToGuild(guildId, guildDirectory.memberIds(guildId),
                    RealtimeMessage.of(RealtimeMessageType.CHAT_MESSAGE, message.toRealtimeData()));
            log.info("길드 채팅 보냄 messageId={} guildId={} accountId={}", message.messageId(), guildId, accountId);
            return ChatMessageResponse.from(message);
        });
    }

    /** CH3 내 길드 채팅 내역. 횟수 제한에 걸리지 않는다 */
    public ChatHistoryResponse guildHistory(Long accountId, String before, Integer size) {
        int limit = size(size);
        if (before != null) {
            checkCursor(before);
        }
        String guildId = guildDirectory.guildIdOf(accountId).orElseThrow(ChatErrors::notGuildMember);
        return ChatHistoryResponse.from(guildChatHistory.loadLatest(guildId, before, limit));
    }

    private ChatMessageResponse runOnce(Long accountId, ChatSendRequest request, ChatScope scope,
                                        Supplier<ChatMessageResponse> action) {
        return idempotencyService.run(accountId, request.requestId().toLowerCase(Locale.ROOT),
                new ChatSendKey(scope, request.text()), ChatMessageResponse.class, action);
    }

    /** 도배 쉬기·횟수 제한(명세 판정 3·4). 막히면 429(남은 초는 올림, 최소 1) */
    private void acquireRate(Long accountId) {
        ChatRateDecision decision = rateLimiter.acquire(accountId);
        switch (decision.outcome()) {
            case ALLOWED -> {
            }
            case RATE_LIMITED -> throw ChatErrors.rateLimited(decision.retryAfterSeconds());
            case MUTED -> throw ChatErrors.muted(decision.retryAfterSeconds());
        }
    }

    /** 보낸 때의 닉네임. 로그인한 계정이 없으면 데이터 버그라 500으로 둔다 */
    private String nicknameOf(Long accountId) {
        String nickname = accountService.getNicknames(List.of(accountId)).get(accountId);
        return Objects.requireNonNull(nickname, () -> "로그인한 계정의 닉네임이 없습니다: " + accountId);
    }

    private int size(Integer size) {
        if (size == null) {
            return guild.pageSize();
        }
        int max = guild.pageSizeMax();
        if (size < 1 || size > max) {
            throw ChatErrors.invalidValue("size", "size는 1~" + max + "이어야 합니다.");
        }
        return size;
    }

    /** before는 앞 묶음의 nextCursor 그대로 = ASCII 10진수(long 범위). 아니면 400 */
    private static void checkCursor(String before) {
        boolean digits = !before.isEmpty() && before.length() <= 19
                && before.chars().allMatch(c -> c >= '0' && c <= '9');
        if (digits) {
            try {
                Long.parseLong(before);
                return;
            } catch (NumberFormatException e) {
                // long보다 큼: 아래에서 400
            }
        }
        throw ChatErrors.invalidValue("before", "before는 앞 묶음의 nextCursor(10진수 문자열)여야 합니다.");
    }

}
