package com.pw01.webserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스모크 테스트: 앱 전체가 test 프로필로 뜨는가.
 * 실패하면 설정 키 오타, 빠진 설정값, 빈 주입 실패, 마이그레이션과 엔티티 불일치(ddl-auto=validate),
 * Docker Desktop이 꺼진 상태를 의심한다.
 */
@IntegrationTest
class ApplicationSmokeTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    /** MySQL·Redis 연결을 포함한 상태 확인이 UP인가 */
    @Test
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

}
