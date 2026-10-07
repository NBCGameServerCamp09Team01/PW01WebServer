package com.pw01.webserver.common.error;

import com.pw01.webserver.MockMvcUtf8Config;
import com.pw01.webserver.auth.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공통 오류 틀 테스트: 오류마다 상태 코드와 본문 {code, message, path, errors}가 루트 docs/contracts 공통 규칙대로 나오는가.
 * 테스트 전용 컨트롤러로 확인하므로 예시 API를 지워도 남는다. DB·Docker 없이 웹 계층만 띄운다(@WebMvcTest).
 * 실패하면 GlobalExceptionHandler의 해당 메서드와 application.properties의 JSON 설정을 의심한다.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ErrorTestController.class)
@ActiveProfiles("test")
@Import({GlobalExceptionHandlerTest.ErrorTestController.class, MockMvcUtf8Config.class})
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    /** 웹 계층 테스트에도 WebConfig(인증 인터셉터)가 올라오므로 인터셉터가 쓰는 AuthService를 목으로 채운다. 이 테스트 경로에는 인터셉터가 걸리지 않는다 */
    @MockitoBean
    AuthService authService;

    @Test
    void validationErrorListsFields() throws Exception {
        mockMvc.perform(post("/test/errors/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.path").value("/test/errors/body"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("이름을 입력해야 합니다."));
    }

    @Test
    void unknownFieldIsRejected() throws Exception {
        mockMvc.perform(post("/test/errors/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"a\", \"nmae\": \"b\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.message").value(containsString("nmae")))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void brokenJsonIsRejected() throws Exception {
        mockMvc.perform(post("/test/errors/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    @Test
    void apiExceptionKeepsStatusAndCode() throws Exception {
        mockMvc.perform(get("/test/errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEST_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("테스트 대상이 없습니다."));

        mockMvc.perform(get("/test/errors/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEST_CONFLICT"));
    }

    @Test
    void pathValueOfWrongTypeIsValidationError() throws Exception {
        mockMvc.perform(get("/test/errors/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    @Test
    void unknownPathAndWrongMethodUseStatusNames() throws Exception {
        mockMvc.perform(get("/test/errors/nowhere"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(delete("/test/errors/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unexpectedErrorHidesCause() throws Exception {
        mockMvc.perform(get("/test/errors/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(not(containsString("내부 정보"))));
    }

    // 확인: Redis 연결 실패·DB 트랜잭션 시작 실패는 503 SERVICE_UNAVAILABLE. 실패하면 handleUnavailable의 대상 예외를 본다
    @Test
    void storageUnreachableIsServiceUnavailable() throws Exception {
        mockMvc.perform(get("/test/errors/redis-down"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.path").value("/test/errors/redis-down"));

        mockMvc.perform(get("/test/errors/db-down"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    // 확인: 연결은 됐는데 제약 위반으로 실패한 것은 503이 아니라 500. 실패하면 503 대상이 너무 넓어졌는지 본다
    @Test
    void constraintViolationStaysInternalError() throws Exception {
        mockMvc.perform(get("/test/errors/constraint"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    /** 테스트 전용 컨트롤러. 오류를 일부러 일으킨다 */
    @RestController
    @RequestMapping("/test/errors")
    static class ErrorTestController {

        record Body(@NotBlank(message = "이름을 입력해야 합니다.") String name) {
        }

        @PostMapping("/body")
        void body(@Valid @RequestBody Body body) {
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("TEST_NOT_FOUND", "테스트 대상이 없습니다.");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("TEST_CONFLICT", "이미 있습니다.");
        }

        @GetMapping("/items/{id}")
        void item(@PathVariable Long id) {
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("내부 정보가 담긴 메시지");
        }

        @GetMapping("/redis-down")
        void redisDown() {
            throw new RedisConnectionFailureException("테스트: Redis 연결 실패");
        }

        @GetMapping("/db-down")
        void dbDown() {
            throw new CannotCreateTransactionException("테스트: DB 연결 실패");
        }

        @GetMapping("/constraint")
        void constraint() {
            throw new DataIntegrityViolationException("테스트: CHECK 위반");
        }

    }

}
