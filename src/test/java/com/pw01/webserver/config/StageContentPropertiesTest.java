package com.pw01.webserver.config;

import com.pw01.webserver.config.StageContentProperties.S3;
import com.pw01.webserver.config.StageContentProperties.Source;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 웨이브 정의 출처 설정(pw01.stage-content.*) 검사 단위 테스트. 틀린 값이면 서버가 뜨지 않아야 한다(배포 때 바로 드러남).
 * 실패하면 StageContentProperties의 compact 생성자 검사(버킷 이름 규칙·키·지역)를 의심한다.
 */
class StageContentPropertiesTest {

    // 확인: classpath는 S3 값이 없어도 된다(로컬·시험)
    @Test
    void classpath는_S3_값이_없어도_됨() {
        assertThat(new StageContentProperties(Source.CLASSPATH, null).source()).isEqualTo(Source.CLASSPATH);
    }

    // 확인: s3면 버킷·키·지역이 모두 맞아야 한다. 자리 표시값(대문자·밑줄)이 남아 있으면 기동 실패
    @Test
    void s3는_버킷_키_지역이_필요함() {
        assertThat(new StageContentProperties(Source.S3,
                new S3("pw01-content-a1b2c3", "content/stage-waves.json", "ap-northeast-2")).s3().bucket())
                .isEqualTo("pw01-content-a1b2c3");

        assertThatThrownBy(() -> new StageContentProperties(Source.S3, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bucket");
        assertThatThrownBy(() -> new StageContentProperties(Source.S3,
                new S3("REPLACE_WITH_BUCKET", "content/stage-waves.json", "ap-northeast-2")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bucket");
        assertThatThrownBy(() -> new StageContentProperties(Source.S3,
                new S3("pw01-content-a1b2c3", "/content/stage-waves.json", "ap-northeast-2")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("key");
        assertThatThrownBy(() -> new StageContentProperties(Source.S3,
                new S3("pw01-content-a1b2c3", "content/stage-waves.json", " ")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("region");
        assertThatThrownBy(() -> new StageContentProperties(null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("source");
    }

}
