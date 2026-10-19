package com.pw01.webserver.stage.service;

import com.pw01.webserver.common.master.MasterDataLoader;
import com.pw01.webserver.stage.repository.ClasspathStageWaveSource;
import com.pw01.webserver.stage.repository.StageWaveFile;
import com.pw01.webserver.stage.repository.StageWaveSource;
import com.pw01.webserver.stage.repository.StageWaveSourceException;
import com.pw01.webserver.stage.service.StageContentReloader.Outcome;
import com.pw01.webserver.stage.service.StageContentReloader.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 웨이브 정의 다시 읽기 규칙 단위 테스트(컨테이너·S3 없음, 판 12 W3). 출처는 시험 안의 가짜(S3처럼 ETag·내용을 바꿀 수 있음).
 * 실제 master/ 리소스로 뜬 정의(w3-m2-d1)에서 출발해, 올린 파일에 따라 바꾸거나(CHANGED) 이전 정의를 지키는지(REJECTED 등) 본다.
 * 실패하면 StageContentReloader.reload의 단계 순서(HEAD → 같은 내용 → version → 기동 검사)와 StageCatalog.build를 의심한다.
 */
class StageContentReloaderTest {

    private static final String SAMURAI_2 = "{ \"monsterId\": \"monster.samurai\", \"count\": 2, \"goldReward\": 3 }";
    private static final String SAMURAI_4 = "{ \"monsterId\": \"monster.samurai\", \"count\": 4, \"goldReward\": 3 }";

    private final MasterDataLoader loader = new MasterDataLoader(
            JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build());
    private final String resource = new String(loader.readClasspath("stage-waves.json"), StandardCharsets.UTF_8);

    private FakeSource source;
    private StageCatalog catalog;
    private StageContentReloader reloader;

    @BeforeEach
    void setUp() {
        source = new FakeSource("\"e1\"", resource);
        catalog = new StageCatalog(loader, source);
        reloader = new StageContentReloader(catalog, source, loader);
    }

    // 확인: 마리 수를 바꾸고 version을 올리면 바뀌고, 그 뒤 판 시작이 쓸 덩어리의 구성도 새 값이다
    @Test
    void 바뀐_파일은_교체() {
        source.put("\"e2\"", resource.replace(SAMURAI_2, SAMURAI_4).replaceFirst("\"version\": 3", "\"version\": 4"));

        Outcome outcome = reloader.reload("signal");

        assertThat(outcome.result()).isEqualTo(Result.CHANGED);
        assertThat(outcome.before()).isEqualTo("w3-m2-d1");
        assertThat(outcome.waveVersion()).isEqualTo("w4-m2-d1");
        assertThat(catalog.waveVersion()).isEqualTo("w4-m2-d1");
        assertThat(catalog.get("stage.01.01").waves().getFirst().enemies().getFirst().count()).isEqualTo(4);
        assertThat(reloader.status().lastRejected()).isNull();
    }

    // 확인: ETag가 같으면 내용을 받지 않는다(안전망 주기 확인이 HEAD 한 번으로 끝남)
    @Test
    void ETag가_같으면_받지_않음() {
        Outcome outcome = reloader.reload("poll");

        assertThat(outcome.result()).isEqualTo(Result.UNCHANGED);
        assertThat(source.reads).isEqualTo(1); // 뜰 때 한 번뿐
        assertThat(reloader.status().lastCheckAt()).isNotNull();
    }

    // 확인: 같은 내용을 다시 올리면(ETag만 다름) 바꾸지 않고, 다음부터는 HEAD에서 끝난다
    @Test
    void 같은_내용을_다시_올리면_그대로() {
        source.put("\"e2\"", resource);

        assertThat(reloader.reload("signal").result()).isEqualTo(Result.UNCHANGED);
        assertThat(reloader.reload("poll").result()).isEqualTo(Result.UNCHANGED);
        assertThat(source.reads).isEqualTo(2);
    }

    // 확인(S-7): 내용이 바뀌었는데 version이 그대로면 거절하고 이전 정의를 지킨다. 같은 파일은 다시 받지 않는다
    @Test
    void version을_안_올리면_거절() {
        source.put("\"e2\"", resource.replace(SAMURAI_2, SAMURAI_4));

        Outcome outcome = reloader.reload("signal");

        assertThat(outcome.result()).isEqualTo(Result.REJECTED);
        assertThat(outcome.reason()).contains("version(3)");
        assertThat(catalog.waveVersion()).isEqualTo("w3-m2-d1");
        assertThat(catalog.get("stage.01.01").waves().getFirst().enemies().getFirst().count()).isEqualTo(2);
        assertThat(reloader.reload("poll").result()).isEqualTo(Result.REJECTED);
        assertThat(source.reads).isEqualTo(2);
        assertThat(reloader.status().lastRejected().reason()).contains("version(3)");
    }

    // 확인: 기동 검사에 걸리는 파일(모르는 스테이지 키)은 거절, 이전 정의 유지
    @Test
    void 기동_검사에_걸리면_거절() {
        source.put("\"e2\"", resource.replace("\"stage.01.02\"", "\"stage.01.99\"")
                .replaceFirst("\"version\": 3", "\"version\": 4"));

        Outcome outcome = reloader.reload("signal");

        assertThat(outcome.result()).isEqualTo(Result.REJECTED);
        assertThat(outcome.reason()).contains("stage-waves.json");
        assertThat(catalog.waveVersion()).isEqualTo("w3-m2-d1");
    }

    // 확인: 모르는 칸이 있는 파일은 거절(리소스 파일과 같은 엄격함)
    @Test
    void 모르는_칸이면_거절() {
        source.put("\"e2\"", resource.replaceFirst("\"version\": 3", "\"version\": 4, \"extra\": 1"));

        assertThat(reloader.reload("signal").result()).isEqualTo(Result.REJECTED);
        assertThat(catalog.waveVersion()).isEqualTo("w3-m2-d1");
    }

    // 확인: 되돌리기(version이 내려감)는 받는다
    @Test
    void 되돌리기는_받음() {
        source.put("\"e2\"", resource.replace(SAMURAI_2, SAMURAI_4).replaceFirst("\"version\": 3", "\"version\": 4"));
        assertThat(reloader.reload("signal").result()).isEqualTo(Result.CHANGED);

        source.put("\"e1\"", resource);
        Outcome back = reloader.reload("signal");

        assertThat(back.result()).isEqualTo(Result.CHANGED);
        assertThat(back.waveVersion()).isEqualTo("w3-m2-d1");
    }

    // 확인: 거절된 뒤 고친 파일을 올리면 받는다(거절 기억이 다음 파일을 막지 않음)
    @Test
    void 거절_뒤_고친_파일은_받음() {
        source.put("\"e2\"", resource.replace(SAMURAI_2, SAMURAI_4));
        assertThat(reloader.reload("signal").result()).isEqualTo(Result.REJECTED);

        source.put("\"e3\"", resource.replace(SAMURAI_2, SAMURAI_4).replaceFirst("\"version\": 3", "\"version\": 5"));

        assertThat(reloader.reload("signal").result()).isEqualTo(Result.CHANGED);
        assertThat(catalog.waveVersion()).isEqualTo("w5-m2-d1");
    }

    // 확인: 출처를 못 읽으면 SOURCE_ERROR, 이전 정의 유지(예외를 밖으로 던지지 않음)
    @Test
    void 출처_오류면_유지() {
        source.failing = true;

        Outcome outcome = reloader.reload("poll");

        assertThat(outcome.result()).isEqualTo(Result.SOURCE_ERROR);
        assertThat(outcome.reason()).contains("status=403");
        assertThat(catalog.waveVersion()).isEqualTo("w3-m2-d1");
    }

    // 확인: 리소스 파일을 쓰는 서버(로컬·시험)는 다시 읽지 않는다
    @Test
    void 리소스_파일이면_다시_읽지_않음() {
        ClasspathStageWaveSource classpath = new ClasspathStageWaveSource(loader);
        StageCatalog classpathCatalog = new StageCatalog(loader, classpath);
        StageContentReloader classpathReloader = new StageContentReloader(classpathCatalog, classpath, loader);

        assertThat(classpathReloader.reload("signal").result()).isEqualTo(Result.NOT_S3);
        assertThat(classpathReloader.reloadable()).isFalse();
    }

    /** S3 객체 하나를 흉내 낸다: put으로 덮어쓰고(새 ETag), failing이면 403 */
    private static final class FakeSource implements StageWaveSource {

        private String etag;
        private String json;
        private int reads;
        private boolean failing;

        FakeSource(String etag, String json) {
            this.etag = etag;
            this.json = json;
        }

        void put(String newEtag, String newJson) {
            this.etag = newEtag;
            this.json = newJson;
        }

        @Override
        public StageWaveFile read() {
            failIfNeeded();
            reads++;
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
            failIfNeeded();
            return etag;
        }

        private void failIfNeeded() {
            if (failing) {
                throw new StageWaveSourceException("fake을 확인하지 못했습니다 status=403", null);
            }
        }
    }

}
