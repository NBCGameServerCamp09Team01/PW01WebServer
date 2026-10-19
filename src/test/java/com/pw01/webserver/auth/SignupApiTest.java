package com.pw01.webserver.auth;

import com.pw01.webserver.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원가입 API(POST /auth/signup)를 앱 전체 + 실제 MySQL(테스트 컨테이너)로 확인한다.
 * 상태 코드·오류 코드·저장 결과(두 행, 비밀번호 해시)를 본다. 요청 칸 규칙의 경계값은 SignupRequestValidationTest가 본다.
 * 실패하면 AuthController 경로, SignupRequest 검증, AccountService의 409 변환, V2 마이그레이션 중 어디가 어긋났는지 본다.
 * 테스트끼리 DB를 함께 쓰므로 아이디·닉네임을 서로 다르게 쓴다.
 */
@IntegrationTest
class SignupApiTest {

    /** UTC ISO-8601, 초 아래는 밀리초까지(0이면 생략), 끝에 Z */
    private static final String UTC_MILLIS = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    /** 가입 성공: 201 + Location, 계정 ID는 문자열. DB에 계정·진행 두 행이 생기고 비밀번호는 BCrypt 해시로만 남는다 */
    @Test
    void signupCreatesAccountAndProgress() throws Exception {
        signup("""
                {"loginId": "Signup01", "password": "testpass01", "nickname": "가입01", "email": null}
                """)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/accounts/me"))
                .andExpect(jsonPath("$.data.accountId").isString())
                .andExpect(jsonPath("$.data.loginId").value("Signup01"))
                .andExpect(jsonPath("$.data.nickname").value("가입01"))
                .andExpect(jsonPath("$.data.createdAt").value(matchesPattern(UTC_MILLIS)));

        Map<String, Object> account = jdbc.queryForMap(
                "SELECT account_id, password_hash, email, status FROM account WHERE login_id = 'Signup01'");
        assertThat((String) account.get("password_hash")).startsWith("$2").isNotEqualTo("testpass01");
        assertThat(account.get("email")).isNull();
        assertThat(account.get("status")).isEqualTo("ACTIVE");

        Map<String, Object> progress = jdbc.queryForMap(
                "SELECT level, experience, stat_points, version FROM account_progress WHERE account_id = ?",
                account.get("account_id"));
        assertThat(progress).containsEntry("level", 1).containsEntry("experience", 0)
                .containsEntry("stat_points", 0).containsEntry("version", 0L);
    }

    /** 이메일을 보내면 그대로 저장된다 */
    @Test
    void emailIsStored() throws Exception {
        signup("""
                {"loginId": "Signup02", "password": "testpass02", "nickname": "가입02", "email": "signup02@example.com"}
                """)
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT email FROM account WHERE login_id = 'Signup02'", String.class))
                .isEqualTo("signup02@example.com");
    }

    /** 아이디는 대소문자를 구분한다: 대소문자만 다른 아이디는 다른 계정으로 가입된다 */
    @Test
    void loginIdsDifferingOnlyInCaseAreDifferentAccounts() throws Exception {
        signup("""
                {"loginId": "Signup03", "password": "testpass03", "nickname": "가입03"}
                """)
                .andExpect(status().isCreated());

        signup("""
                {"loginId": "signup03", "password": "testpass03", "nickname": "가입04"}
                """)
                .andExpect(status().isCreated());
    }

    /** 같은 아이디로 다시 가입: 409 ACCOUNT_LOGIN_ID_DUPLICATED */
    @Test
    void duplicateLoginIdIsConflict() throws Exception {
        signup("""
                {"loginId": "Signup05", "password": "testpass05", "nickname": "가입05"}
                """)
                .andExpect(status().isCreated());

        signup("""
                {"loginId": "Signup05", "password": "testpass05", "nickname": "가입06"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOGIN_ID_DUPLICATED"))
                .andExpect(jsonPath("$.path").value("/auth/signup"));
    }

    /** 닉네임은 대소문자만 다르면 같은 닉네임: 409 ACCOUNT_NICKNAME_DUPLICATED */
    @Test
    void nicknameDifferingOnlyInCaseIsConflict() throws Exception {
        signup("""
                {"loginId": "Signup07", "password": "testpass07", "nickname": "Nick07"}
                """)
                .andExpect(status().isCreated());

        signup("""
                {"loginId": "Signup08", "password": "testpass08", "nickname": "NICK07"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NICKNAME_DUPLICATED"));
    }

    /** 칸 형식 위반(아이디 밑줄, 닉네임 자모, 비밀번호 7자): 400 VALIDATION_FAILED, errors에 칸 이름 전부. 계정은 만들지 않는다 */
    @Test
    void invalidFieldsAreValidationError() throws Exception {
        signup("""
                {"loginId": "sign_09", "password": "short07", "nickname": "ㄱ가입09"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("loginId", "password", "nickname")));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account WHERE login_id = 'sign_09'", Integer.class)).isZero();
    }

    /** 비밀번호는 UTF-8 72바이트까지: 한글 25자(75바이트)는 400(BCrypt 한도를 넘기 전에 막아 500이 되지 않음) */
    @Test
    void passwordOver72BytesIsValidationError() throws Exception {
        String password = "가".repeat(25);
        signup("""
                {"loginId": "Signup10", "password": "%s", "nickname": "가입10"}
                """.formatted(password))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("password")));
    }

    /** 모르는 필드가 있으면 400 INVALID_REQUEST_BODY */
    @Test
    void unknownFieldIsRejected() throws Exception {
        signup("""
                {"loginId": "Signup11", "password": "testpass11", "nickname": "가입11", "extra": 1}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    private ResultActions signup(String json) throws Exception {
        return mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

}
