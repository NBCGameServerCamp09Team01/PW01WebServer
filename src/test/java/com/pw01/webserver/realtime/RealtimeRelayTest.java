package com.pw01.webserver.realtime;

import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.config.RealtimeRelayProperties;
import com.pw01.webserver.realtime.dto.RealtimeMessage;
import com.pw01.webserver.realtime.dto.RealtimeMessageType;
import com.pw01.webserver.realtime.repository.RealtimeRelayPublisher;
import com.pw01.webserver.realtime.service.RealtimeConnection;
import com.pw01.webserver.realtime.service.RealtimeConnectionRegistry;
import com.pw01.webserver.realtime.service.RealtimeHeartbeat;
import com.pw01.webserver.realtime.service.RealtimeLocalDelivery;
import com.pw01.webserver.realtime.service.RealtimeNotifier;
import com.pw01.webserver.realtime.service.RealtimeRelayListener;
import com.pw01.webserver.realtime.service.RealtimeServerId;
import io.lettuce.core.api.async.BaseRedisAsyncCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 서버 사이 전달 통합 테스트(루트 docs/contracts/redis-keys.md "실시간 서버 사이 전달"). 진짜 Redis(Testcontainers) 하나에
 * "서버 A"(앱 컨텍스트 자체)와 "서버 B"(이 테스트가 손으로 만든 구독·연결 목록)를 붙여, 한쪽에서 보낸 메시지가 다른 쪽 연결에 가는지 본다.
 * 실패하면 RealtimeRelayConfig의 구독(채널 둘 + 길드 패턴), RealtimeRelayPublisher의 채널 고르기, 받기 쪽 거르기를 차례로 본다.
 * Docker Desktop이 켜져 있어야 돈다.
 */
@IntegrationTest
class RealtimeRelayTest {

    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_0123";

    @Autowired
    private RealtimeNotifier notifierA;
    @Autowired
    private RealtimeConnectionRegistry registryA;
    @Autowired
    private RedisConnectionFactory connectionFactory;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private RealtimeRelayProperties properties;
    @Autowired
    private JsonMapper jsonMapper;

    private RealtimeConnectionRegistry registryB;
    private RealtimeHeartbeat heartbeatB;
    private RealtimeRelayPublisher publisherB;
    private RedisMessageListenerContainer containerB;

    @BeforeEach
    void startServerB() throws Exception {
        registryB = new RealtimeConnectionRegistry();
        heartbeatB = mock(RealtimeHeartbeat.class);
        RealtimeServerId serverIdB = RealtimeServerId.of("server-b");
        RealtimeLocalDelivery deliveryB = new RealtimeLocalDelivery(registryB, jsonMapper);
        RealtimeRelayListener listenerB = new RealtimeRelayListener(deliveryB, registryB, heartbeatB, properties, serverIdB, jsonMapper);
        publisherB = new RealtimeRelayPublisher(redis, jsonMapper, properties, serverIdB);

        containerB = new RedisMessageListenerContainer();
        containerB.setConnectionFactory(connectionFactory);
        containerB.addMessageListener(listenerB, List.of(new ChannelTopic(properties.globalChannel()),
                new ChannelTopic(properties.targetChannel()), new PatternTopic(properties.guildChannelPrefix() + "*")));
        containerB.afterPropertiesSet();
        containerB.start();
        // 구독이 끝나기 전에 보낸 메시지는 잃는다(Pub/Sub 성질). 전체 채널 구독자가 A·B 둘이 될 때까지 기다리고,
        // 패턴 구독은 수를 따로 셀 수 없어(PUBSUB NUMPAT은 서로 다른 패턴 수) 조금 더 기다린다
        long deadline = System.currentTimeMillis() + 5_000;
        while (subscribers(properties.globalChannel()) < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        Thread.sleep(300);
    }

    @AfterEach
    void stopServerB() throws Exception {
        containerB.stop();
        containerB.destroy();
    }

    // 확인: A에서 길드 7에 보내면 B에 붙은 길드원만 같은 id로 받는다(다른 사람은 못 받음)
    @Test
    void A에서_보낸_길드_메시지를_B의_길드원이_받는다() throws Exception {
        BlockingQueue<String> member = connect(registryB, 1001L);
        BlockingQueue<String> outsider = connect(registryB, 1002L);
        RealtimeMessage message = RealtimeMessage.of(RealtimeMessageType.CHAT_MESSAGE, Map.of("text", "guild hello"));

        notifierA.sendToGuild("7", List.of(1001L, 1003L), message);

        String received = member.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull().contains(message.id()).contains("guild hello");
        assertThat(outsider.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    // 확인: A에서 전체에 보내면 B의 모든 연결이 받는다
    @Test
    void A에서_보낸_전체_메시지를_B의_모두가_받는다() throws Exception {
        BlockingQueue<String> first = connect(registryB, 2001L);
        BlockingQueue<String> second = connect(registryB, 2002L);
        RealtimeMessage message = RealtimeMessage.of(RealtimeMessageType.CHAT_MESSAGE, Map.of("text", "to all"));

        notifierA.sendToAll(message);

        assertThat(first.poll(5, TimeUnit.SECONDS)).contains(message.id());
        assertThat(second.poll(5, TimeUnit.SECONDS)).contains(message.id());
    }

    // 확인: 거꾸로 B가 올린 지정 메시지를 A(앱)의 연결이 받는다 — 앱의 구독 컨테이너(RealtimeRelayConfig)가 도는지
    @Test
    void B에서_보낸_지정_메시지를_A의_연결이_받는다() throws Exception {
        BlockingQueue<String> onA = connect(registryA, 3001L);
        RealtimeMessage message = RealtimeMessage.of(RealtimeMessageType.CHAT_MESSAGE, Map.of("text", "from b"));

        publisherB.publishToAccounts(List.of(3001L), message);

        assertThat(onA.poll(5, TimeUnit.SECONDS)).contains(message.id());
        // 앱 컨텍스트는 다른 통합 테스트와 같이 쓰므로 가짜 연결을 치운다
        registryA.find(3001L).ifPresent(registryA::unregister);
    }

    // 확인: 한 서버에서 연달아 보낸 메시지는 다른 서버 연결에 같은 순서로 간다(받기 스레드 하나)
    @Test
    void 연달아_보낸_메시지는_순서대로_간다() throws Exception {
        BlockingQueue<String> onB = connect(registryB, 4001L);

        for (int i = 0; i < 10; i++) {
            notifierA.send(4001L, RealtimeMessage.of(RealtimeMessageType.CHAT_MESSAGE, Map.of("seq", i)));
        }

        for (int i = 0; i < 10; i++) {
            assertThat(onB.poll(5, TimeUnit.SECONDS)).contains("\"seq\":" + i);
        }
    }

    // 확인: A에서 다른 곳 로그인 확인을 넘기면 B에 붙은 그 계정 연결을 Pong과 같은 판정으로 다시 확인한다(LB-1)
    @Test
    void 세션_다시_확인이_다른_서버로_간다() throws Exception {
        connect(registryB, 5001L);

        notifierA.requestSessionCheck(5001L);

        verify(heartbeatB, timeout(5_000)).revalidate(any(RealtimeConnection.class));
    }

    /** 채널 구독자 수(PUBSUB NUMSUB). Spring의 execute는 이 명령의 응답을 읽지 못해 Lettuce 명령을 바로 쓴다 */
    @SuppressWarnings("unchecked")
    private long subscribers(String channel) {
        Long count = redis.execute((RedisCallback<Long>) connection -> {
            BaseRedisAsyncCommands<byte[], byte[]> commands = (BaseRedisAsyncCommands<byte[], byte[]>) connection.getNativeConnection();
            try {
                Map<byte[], Long> counts = commands.pubsubNumsub(channel.getBytes(StandardCharsets.UTF_8)).get(2, TimeUnit.SECONDS);
                return counts.values().stream().findFirst().orElse(0L);
            } catch (Exception e) {
                return 0L;
            }
        });
        return count == null ? 0 : count;
    }

    /** 가짜 게임 연결을 등록하고, 받은 텍스트를 차례로 담는 줄을 돌려준다 */
    private static BlockingQueue<String> connect(RealtimeConnectionRegistry registry, Long accountId) throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);
        when(socket.getId()).thenReturn("s" + accountId);
        doAnswer(invocation -> {
            WebSocketMessage<?> message = invocation.getArgument(0);
            if (message instanceof TextMessage text) {
                received.add(text.getPayload());
            }
            return null;
        }).when(socket).sendMessage(any());
        registry.register(new RealtimeConnection(socket, accountId, TOKEN));
        return received;
    }

}
