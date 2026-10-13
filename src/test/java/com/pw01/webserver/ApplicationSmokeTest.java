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

    /**
     * health가 DB·Redis 하위 상태를 따로 보여 주는가(장애 때 어느 쪽인지 보려고).
     * 실패하면 management.endpoint.health.show-details 값을 의심한다
     */
    @Test
    void healthShowsDbAndRedis() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("UP"));
    }

    /**
     * info에 커밋(APP_COMMIT 없으면 local)과 빌드 버전이 실리는가.
     * 실패하면 exposure.include의 info, management.info.env.enabled, build.gradle의 buildInfo()를 의심한다.
     * IDE 실행기로 돌려 build만 실패하면 build-info.properties가 안 만들어진 것이니 Gradle로 돌린다
     */
    @Test
    void infoShowsCommitAndBuild() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.commit").value("local"))
                .andExpect(jsonPath("$.build.version").exists());
    }

}
