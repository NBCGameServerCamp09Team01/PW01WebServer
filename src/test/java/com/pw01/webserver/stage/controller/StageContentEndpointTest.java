package com.pw01.webserver.stage.controller;

import com.pw01.webserver.common.master.MasterDataLoader;
import com.pw01.webserver.realtime.service.RealtimeNotifier;
import com.pw01.webserver.common.server.ServerId;
import com.pw01.webserver.stage.repository.StageWaveFile;
import com.pw01.webserver.stage.repository.StageWaveSource;
import com.pw01.webserver.stage.service.StageCatalog;
import com.pw01.webserver.stage.service.StageContentReloader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 관리 포트 다시 읽기(POST /actuator/stagecontent) 단위 테스트(판 12 W4). 바뀌었을 때만 다른 서버에 알리는지,
 * 응답 모양(result·before·waveVersion·reason·serverId)이 명세(ops-health.md)와 같은지 본다.
 * 실패하면 StageContentEndpoint.reload의 CHANGED 판단과 reason 넣는 조건을 의심한다.
 */
class StageContentEndpointTest {

    private final MasterDataLoader loader = new MasterDataLoader(
            JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build());
    private final String resource = new String(loader.readClasspath("stage-waves.json"), StandardCharsets.UTF_8);

    private MutableSource source;
    private RealtimeNotifier notifier;
    private StageContentEndpoint endpoint;

    @BeforeEach
    void setUp() {
        source = new MutableSource("\"e1\"", resource);
        StageCatalog catalog = new StageCatalog(loader, source);
        notifier = mock(RealtimeNotifier.class);
        endpoint = new StageContentEndpoint(new StageContentReloader(catalog, source, loader), notifier,
                ServerId.of("srv-a"));
    }

    // 확인: 바뀌면 CHANGED + 다른 서버에 알림, reason 칸 없음
    @Test
    void 바뀌면_다른_서버에_알림() {
        source.put("\"e2\"", resource.replaceFirst("\"version\": 3", "\"version\": 4"));

        Map<String, Object> body = endpoint.reload();

        assertThat(body).containsEntry("result", "CHANGED").containsEntry("before", "w3-m2-d1")
                .containsEntry("waveVersion", "w4-m2-d1").containsEntry("serverId", "srv-a").doesNotContainKey("reason");
        verify(notifier).notifyContentChanged();
    }

    // 확인: 거절이면 알리지 않고 reason을 준다. 바뀌지 않았어도 알리지 않는다
    @Test
    void 거절이나_그대로면_알리지_않음() {
        assertThat(endpoint.reload()).containsEntry("result", "UNCHANGED");

        source.put("\"e2\"", resource.replace("\"count\": 2, \"goldReward\": 3", "\"count\": 4, \"goldReward\": 3"));
        Map<String, Object> body = endpoint.reload();

        assertThat(body).containsEntry("result", "REJECTED").containsEntry("waveVersion", "w3-m2-d1");
        assertThat((String) body.get("reason")).contains("version(3)");
        verify(notifier, never()).notifyContentChanged();
        assertThat(endpoint.status()).containsEntry("waveVersion", "w3-m2-d1").containsKey("lastRejected");
    }

    /** S3 객체 하나 흉내(시험용) */
    private static final class MutableSource implements StageWaveSource {

        private String etag;
        private String json;

        MutableSource(String etag, String json) {
            this.etag = etag;
            this.json = json;
        }

        void put(String newEtag, String newJson) {
            this.etag = newEtag;
            this.json = newJson;
        }

        @Override
        public StageWaveFile read() {
            return new StageWaveFile(json.getBytes(StandardCharsets.UTF_8), etag);
        }

        @Override
        public String describe() {
            return "fake";
        }

        @Override
        public boolean reloadable() {
            return true;
        }

        @Override
        public String currentEtag() {
            return etag;
        }
    }

}
