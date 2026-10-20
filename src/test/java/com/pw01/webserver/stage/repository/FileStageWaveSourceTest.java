package com.pw01.webserver.stage.repository;

import com.pw01.webserver.common.master.MasterDataLoader;
import com.pw01.webserver.stage.service.StageCatalog;
import com.pw01.webserver.stage.service.StageContentReloader;
import com.pw01.webserver.stage.service.StageContentReloader.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 로컬 시험용 파일 출처(source=file) 단위 테스트. 임시 폴더의 실제 파일을 고쳐 가며 본다.
 * ETag가 내용 지문이라 같은 내용이면 같고 다르면 달라야 하며, 교체기와 붙였을 때 재시작 없이 바뀌어야 한다.
 * 실패하면 FileStageWaveSource.etagOf(내용 지문)와 파일 읽기 예외 변환을 의심한다.
 */
class FileStageWaveSourceTest {

    private final MasterDataLoader loader = new MasterDataLoader(
            JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build());
    private final String resource = new String(loader.readClasspath("stage-waves.json"), StandardCharsets.UTF_8);

    @TempDir
    Path dir;

    // 확인: 읽기·ETag. 같은 내용을 다시 저장하면 ETag가 그대로, 내용이 바뀌면 달라진다
    @Test
    void ETag는_내용_지문() throws Exception {
        Path file = write(resource);
        FileStageWaveSource source = new FileStageWaveSource(file);

        StageWaveFile first = source.read();
        assertThat(first.content()).isEqualTo(resource.getBytes(StandardCharsets.UTF_8));
        assertThat(first.etag()).startsWith("\"sha256-").isEqualTo(source.currentEtag());
        assertThat(source.reloadable()).isTrue();
        assertThat(source.describe()).startsWith("file:");

        write(resource);
        assertThat(source.currentEtag()).isEqualTo(first.etag());

        write(resource.replaceFirst("\"version\": 3", "\"version\": 4"));
        assertThat(source.currentEtag()).isNotEqualTo(first.etag());
    }

    // 확인: 파일이 없으면 원인을 말하는 예외(뜰 때면 기동 실패, 실행 중이면 교체기가 SOURCE_ERROR로 유지)
    @Test
    void 파일이_없으면_예외() {
        FileStageWaveSource source = new FileStageWaveSource(dir.resolve("none.json"));

        assertThatThrownBy(source::read).isInstanceOf(StageWaveSourceException.class).hasMessageContaining("없습니다");
        assertThatThrownBy(source::currentEtag).isInstanceOf(StageWaveSourceException.class);
    }

    // 확인: 교체기와 붙이면 파일을 고쳐 저장한 뒤 다시 읽기로 재시작 없이 바뀐다(로컬 compose 시험과 같은 길)
    @Test
    void 교체기와_붙이면_재시작_없이_바뀜() throws Exception {
        Path file = write(resource);
        FileStageWaveSource source = new FileStageWaveSource(file);
        StageCatalog catalog = new StageCatalog(loader, source);
        StageContentReloader reloader = new StageContentReloader(catalog, source, loader);

        assertThat(reloader.reload("signal").result()).isEqualTo(Result.UNCHANGED);

        write(resource.replaceFirst("\"version\": 3", "\"version\": 4"));
        assertThat(reloader.reload("signal").result()).isEqualTo(Result.CHANGED);
        assertThat(catalog.waveVersion()).isEqualTo("w4-m3-d1");
    }

    private Path write(String json) throws Exception {
        return Files.writeString(dir.resolve("stage-waves.json"), json, StandardCharsets.UTF_8);
    }

}
