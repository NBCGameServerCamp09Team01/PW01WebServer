package com.pw01.webserver.realtime;

import com.jayway.jsonpath.JsonPath;
import com.pw01.webserver.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실시간 연결 통합 테스트(루트 docs/contracts/realtime-api.md). 실제 포트로 앱을 띄우고 진짜 WebSocket 클라이언트로 붙는다.
 * MockMvc로는 핸드셰이크·Ping/Pong을 볼 수 없어 @IntegrationTest 대신 RANDOM_PORT를 쓴다(컨테이너는 같은 설정).
 * 시간을 줄이려고 세션 수명 4초, Ping 1초, 허용 Pong 없음 2번으로 띄운다(2초 < 4초, 값의 관계는 그대로).
 * Docker Desktop이 켜져 있어야 돈다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "pw01.auth.session.ttl=4s",
        "pw01.realtime.ping-interval=1s",
        "pw01.realtime.max-missed-pongs=2"
})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RealtimeApiTest {

    private static final String PASSWORD = "password123";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final HttpClient http = HttpClient.newHttpClient();
    private final StandardWebSocketClient webSocketClient = new StandardWebSocketClient();

    @Value("${local.server.port}")
    private int port;

    // 확인: 토큰 없이 핸드셰이크하면 HTTP와 같은 401 AUTH_TOKEN_MISSING. 실패하면 핸드셰이크 인터셉터 등록(RealtimeConfig)을 본다
    @Test
    void 토큰이_없으면_401() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(httpUri("/realtime")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("AUTH_TOKEN_MISSING");
    }

    // 확인: UE(libwebsockets)처럼 Origin에 접속 주소를 실어 보내도 연결된다. 실패하면 RealtimeConfig의 setAllowedOrigins를 본다
    // (Spring 기본값은 같은 출처만 허용해 403이 된다. 10/11 UE PIE에서 발견)
    @Test
    void Origin을_보내는_클라이언트도_연결된다() throws Exception {
        String token = login(signup());
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Authorization", "Bearer " + token);
        headers.setOrigin("localhost");

        WebSocketSession session = webSocketClient.execute(new Client(), headers,
                URI.create("ws://localhost:" + port + "/realtime")).get(3, TimeUnit.SECONDS);

        assertThat(session.isOpen()).isTrue();
        session.close();
    }

    // 확인: 같은 계정이 다른 곳에서 로그인하면 이전 연결이 SESSION_REPLACED를 받고 4001로 닫힌다(다음 Ping을 기다리지 않음)
    @Test
    void 다른_곳에서_로그인하면_이전_연결에_바로_알린다() throws Exception {
        String loginId = signup();
        Client previous = connect(login(loginId));

        login(loginId);

        String message = previous.messages.poll(3, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat((String) JsonPath.read(message, "$.type")).isEqualTo("SESSION_REPLACED");
        assertThat((String) JsonPath.read(message, "$.id")).isNotBlank();
        assertThat(previous.closed.get(3, TimeUnit.SECONDS).getCode()).isEqualTo(4001);
    }

    // 확인: 같은 토큰으로 다시 연결하면 이전 연결은 4000, 새 연결은 남는다
    @Test
    void 같은_세션으로_다시_연결하면_이전_연결은_4000() throws Exception {
        String token = login(signup());
        Client first = connect(token);

        Client second = connect(token);

        assertThat(first.closed.get(3, TimeUnit.SECONDS).getCode()).isEqualTo(4000);
        assertThat(second.session.isOpen()).isTrue();
    }

    // 확인: 로그아웃하면 그 세션의 연결이 1000으로 닫힌다
    @Test
    void 로그아웃하면_1000으로_닫힌다() throws Exception {
        String token = login(signup());
        Client client = connect(token);

        HttpResponse<String> logout = http.send(HttpRequest.newBuilder(httpUri("/auth/logout"))
                .header("Authorization", "Bearer " + token).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(client.closed.get(3, TimeUnit.SECONDS).getCode()).isEqualTo(1000);
    }

    // 확인: 다른 HTTP 요청이 없어도 연결의 Pong이 세션을 이어 준다(수명 4초보다 오래 기다린 뒤에도 인증 요청이 200).
    // 실패하면 Pong 연장(RealtimeHeartbeat.onPong → AuthService.extendSession)을 본다
    @Test
    void 연결해_두면_세션_수명보다_오래_살아_있다() throws Exception {
        String token = login(signup());
        Client client = connect(token);

        Thread.sleep(6_000);

        assertThat(client.session.isOpen()).isTrue();
        assertThat(accountsMe(token).statusCode()).isEqualTo(200);
    }

    // 확인(대조): 연결하지 않으면 같은 시간 뒤 세션이 끝난다. 위 시험이 짧은 수명 덕에 의미가 있는지 확인한다
    @Test
    void 연결하지_않으면_세션_수명이_지나면_끝난다() throws Exception {
        String token = login(signup());

        Thread.sleep(6_000);

        assertThat(accountsMe(token).statusCode()).isEqualTo(401);
    }

    private String signup() throws Exception {
        String loginId = "rt" + System.currentTimeMillis() % 1_000_000 + SEQUENCE.incrementAndGet();
        String body = "{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\",\"nickname\":\"" + loginId + "\"}";
        HttpResponse<String> response = post("/auth/signup", body);
        assertThat(response.statusCode()).isEqualTo(201);
        return loginId;
    }

    private String login(String loginId) throws Exception {
        HttpResponse<String> response = post("/auth/login",
                "{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.data.accessToken");
    }

    private HttpResponse<String> accountsMe(String token) throws Exception {
        return http.send(HttpRequest.newBuilder(httpUri("/accounts/me")).header("Authorization", "Bearer " + token)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return http.send(HttpRequest.newBuilder(httpUri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private Client connect(String token) throws Exception {
        Client client = new Client();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Authorization", "Bearer " + token);
        client.session = webSocketClient.execute(client, headers, URI.create("ws://localhost:" + port + "/realtime"))
                .get(3, TimeUnit.SECONDS);
        return client;
    }

    private URI httpUri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** 받은 텍스트 메시지와 종료 상태를 모은다 */
    private static final class Client extends TextWebSocketHandler {

        private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
        private WebSocketSession session;

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closed.complete(status);
        }

    }

}
