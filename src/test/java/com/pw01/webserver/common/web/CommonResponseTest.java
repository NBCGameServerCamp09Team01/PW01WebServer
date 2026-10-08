package com.pw01.webserver.common.web;

import com.jayway.jsonpath.JsonPath;
import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.common.error.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공통 응답 규칙(루트 docs/contracts/README.md "성공 응답"·"오류 응답")을 앱 전체로 확인한다.
 * 성공은 {data, meta}이고 meta.requestId = X-Request-Id, 오류는 감싸지 않고 retryable이 있으며, 204는 본문이 없다.
 * 실패하면 ApiResponseAdvice(감쌀 대상 고르기), RequestIdFilter, ErrorResponse를 의심한다.
 * 테스트끼리 DB를 함께 쓰므로 아이디·닉네임을 서로 다르게 쓴다(Resp01~).
 */
@IntegrationTest
class CommonResponseTest {

    @Autowired
    MockMvc mockMvc;

    /** 성공: 본문은 {data, meta}, meta.requestId는 응답 헤더 X-Request-Id와 같다 */
    @Test
    void successIsWrappedWithRequestId() throws Exception {
        MockHttpServletResponse response = signup("Resp01", "응답01")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.loginId").value("Resp01"))
                .andExpect(jsonPath("$.meta.requestId").isString())
                .andReturn().getResponse();

        String requestId = JsonPath.read(response.getContentAsString(), "$.meta.requestId");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(requestId);
    }

    /** 오류(4xx): 감싸지 않고 retryable=false, 요청 번호는 헤더로만 */
    @Test
    void clientErrorIsNotWrappedAndNotRetryable() throws Exception {
        signup("Resp02", "응답02").andExpect(status().isCreated());

        signup("Resp02", "응답03")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOGIN_ID_DUPLICATED"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(header().exists(RequestIdFilter.HEADER));

        mockMvc.perform(get("/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.retryable").value(false));
    }

    /** 5xx는 retryable=true(서버 쪽 문제라 다시 보내면 될 수 있음) */
    @Test
    void serverErrorIsRetryable() {
        assertThat(ErrorResponse.of("SERVICE_UNAVAILABLE", "잠시 뒤", "/x", HttpStatus.SERVICE_UNAVAILABLE).retryable()).isTrue();
        assertThat(ErrorResponse.of("INTERNAL_ERROR", "오류", "/x", HttpStatus.INTERNAL_SERVER_ERROR).retryable()).isTrue();
        assertThat(ErrorResponse.of("NOT_FOUND", "없음", "/x", HttpStatus.NOT_FOUND).retryable()).isFalse();
    }

    /** 204(로그아웃)는 본문 없이 헤더만 */
    @Test
    void noContentHasNoBody() throws Exception {
        signup("Resp03", "응답04").andExpect(status().isCreated());
        String login = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "Resp03", "password": "resppass01"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(login, "$.data.accessToken");

        MockHttpServletResponse response = mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(header().exists(RequestIdFilter.HEADER))
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).isEmpty();
    }

    private ResultActions signup(String loginId, String nickname) throws Exception {
        return mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"loginId": "%s", "password": "resppass01", "nickname": "%s"}
                        """.formatted(loginId, nickname)));
    }

}
