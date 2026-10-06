package com.pw01.webserver.example;

import com.pw01.webserver.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 학습용 예시 API 테스트(예시 API와 함께 지운다). 루트 docs/contracts/example-api.md의 상태 코드와 본문을 확인한다.
 * 실제 MySQL(테스트 컨테이너)에 저장하고 읽는다. 테스트끼리 DB를 함께 쓰므로 이름을 서로 다르게 쓴다.
 * 실패하면 명세와 Controller·Service·마이그레이션 중 어디가 어긋났는지 본다.
 */
@IntegrationTest
class ExampleApiTest {

    /** UTC ISO-8601, 초 아래는 밀리초까지(0이면 생략), 끝에 Z */
    private static final String UTC_MILLIS = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z";

    @Autowired
    MockMvc mockMvc;

    @Test
    void createThenGet() throws Exception {
        String location = create("""
                {"name": "create-then-get", "description": "학습용 예시입니다."}
                """)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/examples/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("create-then-get"))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern(UTC_MILLIS)))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("create-then-get"))
                .andExpect(jsonPath("$.description").value("학습용 예시입니다."))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern(UTC_MILLIS)));
    }

    @Test
    void blankNameIsValidationError() throws Exception {
        create("""
                {"name": "  "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path").value("/api/v1/examples"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("이름을 입력해야 합니다."));
    }

    @Test
    void unknownFieldIsRejected() throws Exception {
        create("""
                {"name": "unknown-field", "nmae": "typo"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    @Test
    void sameNameIsConflict() throws Exception {
        String body = """
                {"name": "same-name"}
                """;
        create(body).andExpect(status().isCreated());

        create(body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAMPLE_NAME_DUPLICATED"));
    }

    @Test
    void missingIdIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/examples/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXAMPLE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/examples/999999"));
    }

    @Test
    void nonNumericIdIsValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/examples/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    private ResultActions create(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/examples")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

}
