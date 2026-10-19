package com.pw01.webserver.common.master;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 마스터 데이터 로더(SF-7) 단위 테스트. 시험 리소스 master-test/의 파일을 읽는다(컨테이너 없음).
 * 앱과 같은 설정(모르는 칸이면 실패)의 JsonMapper를 직접 만든다.
 * 실패하면 파일 경로(폴더 + 파일 이름), 예외 감싸기, JsonMapper 설정을 의심한다.
 */
class MasterDataLoaderTest {

    record Sample(String name, int count) {
    }

    private final MasterDataLoader loader = new MasterDataLoader(
            JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build(),
            "master-test/");

    // 확인: 정상 파일을 record로 읽는다
    @Test
    void 정상_파일을_읽는다() {
        Sample sample = loader.load("ok.json", Sample.class);

        assertThat(sample.name()).isEqualTo("시험");
        assertThat(sample.count()).isEqualTo(3);
    }

    // 확인: record에 없는 칸(오타)이 있으면 기동 실패 예외, 메시지에 파일 이름
    @Test
    void 모르는_칸이면_실패() {
        assertThatThrownBy(() -> loader.load("unknown-field.json", Sample.class))
                .isInstanceOf(MasterDataException.class)
                .hasMessageContaining("[master/unknown-field.json]");
    }

    // 확인: 파일이 없으면 기동 실패 예외
    @Test
    void 파일이_없으면_실패() {
        assertThatThrownBy(() -> loader.load("missing.json", Sample.class))
                .isInstanceOf(MasterDataException.class)
                .hasMessageContaining("파일이 없습니다");
    }

    // 확인: JSON이 깨졌으면 기동 실패 예외(원인 예외를 붙임)
    @Test
    void 깨진_JSON이면_실패() {
        assertThatThrownBy(() -> loader.load("broken.json", Sample.class))
                .isInstanceOf(MasterDataException.class)
                .hasCauseInstanceOf(tools.jackson.core.JacksonException.class);
    }

}
