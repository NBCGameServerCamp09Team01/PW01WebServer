package com.pw01.webserver.stage.controller;

import com.pw01.webserver.MockMvcUtf8Config;
import com.pw01.webserver.auth.service.AuthService;
import com.pw01.webserver.stage.service.RegionDef;
import com.pw01.webserver.stage.service.StageCatalog;
import com.pw01.webserver.stage.service.StageDef;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ST1 스테이지 정의(GET /stages) 웹 계층 테스트. 스테이지 정보(StageCatalog)는 목으로 둔다.
 * 공개 API라 토큰 없이 200이고 인증 판단을 부르지 않는지, 칸 이름·타입이 stage-api.md와 같은지 본다
 * (계정 진행 칸 status·firstClearedAt은 ST1에 없다).
 * 실패하면 StageController 경로, DTO 칸 이름, WebConfig 인증 경로에 /stages가 들어갔는지를 의심한다.
 */
@WebMvcTest(controllers = StageController.class)
@ActiveProfiles("test")
@Import(MockMvcUtf8Config.class)
class StageControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    StageCatalog stageCatalog;

    // 확인: 토큰 없이 정의가 명세 모양으로 나가고, 인증 판단을 부르지 않는다
    @Test
    void 토큰_없이_정의() throws Exception {
        when(stageCatalog.regions()).thenReturn(List.of(new RegionDef("region.01", "지역 1", 1, List.of(
                new StageDef("stage.01.01", "region.01", "스테이지 1", 1, null, 5),
                new StageDef("stage.01.02", "region.01", "스테이지 2", 2, "stage.01.01", 5)))));
        when(stageCatalog.totalCount()).thenReturn(2);

        mockMvc.perform(get("/stages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.regions[0].regionId").value("region.01"))
                .andExpect(jsonPath("$.data.regions[0].order").value(1))
                .andExpect(jsonPath("$.data.regions[0].stages[1].stageId").value("stage.01.02"))
                .andExpect(jsonPath("$.data.regions[0].stages[1].requires").value("stage.01.01"))
                .andExpect(jsonPath("$.data.regions[0].stages[1].waveCount").value(5))
                .andExpect(jsonPath("$.data.regions[0].stages[0].status").doesNotExist())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
                // 없는 값도 칸은 있다(null). jsonPath의 isEmpty는 칸이 없어도 통과하므로 본문으로 본다
                .andExpect(content().string(containsString("\"requires\":null")));

        verify(authService, never()).authenticate(any());
    }

}
