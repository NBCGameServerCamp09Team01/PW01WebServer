package com.pw01.webserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
     * 게임용 상태 확인 GET /health가 토큰 없이 200 UP이고, 하위 상태(components)는 보이지 않는가.
     * 401이면 PublicPaths에 /health가 빠진 것, 404면 management.endpoint.health.group.game.additional-path,
     * components가 보이면 group.game.show-details를 의심한다
     */
    @Test
    void gameHealthIsUpWithoutDetails() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
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

    /**
     * 판 12: info에 지금 웨이브 정의(content)가 실리고, 관리 엔드포인트 stagecontent가 열리는가.
     * 시험은 리소스 파일을 쓰므로 다시 읽기는 NOT_S3이고 다른 서버에 알리지 않는다.
     * 404면 exposure.include의 stagecontent·StageContentEndpoint의 @Component, content가 없으면 StageContentInfoContributor를 의심한다
     */
    @Test
    void stageContentIsVisible() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.source").value("classpath:master/stage-waves.json"))
                .andExpect(jsonPath("$.content.waveVersion").value("w3-m3-d1"));
        mockMvc.perform(get("/actuator/stagecontent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waveVersion").value("w3-m3-d1"))
                .andExpect(jsonPath("$.serverId").exists());
        mockMvc.perform(post("/actuator/stagecontent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("NOT_S3"))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

}
