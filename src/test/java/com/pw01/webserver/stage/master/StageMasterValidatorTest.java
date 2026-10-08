package com.pw01.webserver.stage.master;

import com.pw01.webserver.common.master.MasterDataException;
import com.pw01.webserver.stage.master.StageMasterFile.RegionEntry;
import com.pw01.webserver.stage.master.StageMasterFile.StageEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * stages.json 검사 규칙 1~8 단위 테스트. 파일 대신 record를 코드로 만든다.
 * 규칙마다 틀린 경우 하나씩 넣어 기동 실패 예외와 걸린 키가 메시지에 있는지 본다.
 * 실패하면 StageMasterValidator의 해당 규칙을 의심한다.
 */
class StageMasterValidatorTest {

    private static StageEntry stage(String id, int order, String requires) {
        return new StageEntry(id, "스테이지", order, requires, 5);
    }

    private static StageMasterFile file(RegionEntry... regions) {
        return new StageMasterFile(List.of(regions));
    }

    private static RegionEntry region(String id, int order, StageEntry... stages) {
        return new RegionEntry(id, "지역", order, List.of(stages));
    }

    private static void assertFails(StageMasterFile file, String messagePart) {
        assertThatThrownBy(() -> StageMasterValidator.validate(file))
                .isInstanceOf(MasterDataException.class)
                .hasMessageContaining("[master/stages.json]")
                .hasMessageContaining(messagePart);
    }

    // 확인: 지금 쓰는 모양(지역 1, 스테이지 2개, 둘째가 첫째를 requires)은 통과
    @Test
    void 정상() {
        assertThatCode(() -> StageMasterValidator.validate(file(region("region.01", 1,
                stage("stage.01.01", 1, null),
                stage("stage.01.02", 2, "stage.01.01")))))
                .doesNotThrowAnyException();
    }

    // 규칙 1: 지역이 없거나, 스테이지가 없는 지역
    @Test
    void 지역_또는_스테이지가_없음() {
        assertFails(new StageMasterFile(List.of()), "지역이 하나도 없습니다");
        assertFails(new StageMasterFile(null), "지역이 하나도 없습니다");
        assertFails(file(new RegionEntry("region.01", "지역", 1, List.of())), "region.01");
    }

    // 규칙 2: 키 모양(대문자·밑줄 금지), 빈 이름
    @Test
    void 키_모양과_이름() {
        assertFails(file(region("Region.01", 1, stage("stage.01.01", 1, null))), "Region.01");
        assertFails(file(region("region.01", 1, stage("stage_01_01", 1, null))), "stage_01_01");
        assertFails(file(region("region.01", 1, new StageEntry("stage.01.01", " ", 1, null, 5))), "name");
    }

    // 규칙 3: 지역 키·스테이지 키 중복(스테이지는 지역이 달라도 전체에서 하나)
    @Test
    void 키_중복() {
        assertFails(file(
                region("region.01", 1, stage("stage.01.01", 1, null)),
                region("region.01", 2, stage("stage.02.01", 1, null))), "지역 키가 겹칩니다");
        assertFails(file(
                region("region.01", 1, stage("stage.01.01", 1, null)),
                region("region.02", 2, stage("stage.01.01", 1, null))), "스테이지 키가 겹칩니다");
    }

    // 규칙 4: order는 1 이상, 지역끼리·같은 지역 스테이지끼리 겹치지 않음
    @Test
    void order() {
        assertFails(file(region("region.01", 0, stage("stage.01.01", 1, null))), "region.01");
        assertFails(file(region("region.01", 1,
                stage("stage.01.01", 1, null),
                stage("stage.01.02", 1, "stage.01.01"))), "order가 겹칩니다");
        assertFails(file(
                region("region.01", 1, stage("stage.01.01", 1, null)),
                region("region.02", 1, stage("stage.02.01", 1, null))), "order가 겹칩니다");
    }

    // 규칙 5: waveCount는 1 이상(파일에서 칸이 빠지면 0으로 들어온다)
    @Test
    void 웨이브_수() {
        assertFails(file(region("region.01", 1, new StageEntry("stage.01.01", "스테이지", 1, null, 0))),
                "waveCount");
    }

    // 규칙 6: requires는 있는 다른 스테이지
    @Test
    void requires_대상() {
        assertFails(file(region("region.01", 1,
                stage("stage.01.01", 1, null),
                stage("stage.01.02", 2, "stage.01.09"))), "stage.01.09");
        assertFails(file(region("region.01", 1,
                stage("stage.01.01", 1, null),
                stage("stage.01.02", 2, "stage.01.02"))), "자기 자신");
    }

    // 규칙 7: requires 순환(첫 스테이지는 따로 있어도 순환은 막는다)
    @Test
    void requires_순환() {
        assertFails(file(region("region.01", 1,
                stage("stage.01.01", 1, null),
                stage("stage.01.02", 2, "stage.01.03"),
                stage("stage.01.03", 3, "stage.01.02"))), "순환");
    }

    // 규칙 8: requires가 없는 스테이지가 하나도 없으면 새 계정이 할 수 있는 것이 없다
    @Test
    void 처음부터_열린_스테이지가_없음() {
        assertFails(file(region("region.01", 1,
                stage("stage.01.01", 1, "stage.01.02"),
                stage("stage.01.02", 2, "stage.01.01"))), "처음부터 열린");
    }

}
