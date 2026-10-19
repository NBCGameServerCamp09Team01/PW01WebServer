package com.pw01.webserver.stage.master;

import com.pw01.webserver.common.master.MasterDataException;
import com.pw01.webserver.stage.master.DifficultyMasterFile.DifficultyEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * difficulties.json 검사 규칙(D1~D5) 단위 테스트. 파일 대신 record를 코드로 만든다.
 * 규칙마다 틀린 경우 하나씩 넣어 기동 실패 예외와 메시지를 본다. 실패하면 DifficultyMasterValidator의 해당 규칙을 의심한다.
 */
class DifficultyMasterValidatorTest {

    private static final DifficultyEntry EASY = new DifficultyEntry(0, "이지", 1.0, 1.0, 1.0, 1.0, 100);
    private static final DifficultyEntry NORMAL = new DifficultyEntry(1, "노말", 1.0, 1.2, 1.2, 1.0, 100);

    private static void assertFails(DifficultyMasterFile file, String part) {
        assertThatThrownBy(() -> DifficultyMasterValidator.validate(file))
                .isInstanceOf(MasterDataException.class)
                .hasMessageContaining("[master/difficulties.json]")
                .hasMessageContaining(part);
    }

    // 확인: 정상 모양이면 번호 목록(0부터 순서대로)
    @Test
    void 정상() {
        assertThat(DifficultyMasterValidator.validate(new DifficultyMasterFile(1, List.of(EASY, NORMAL))))
                .containsExactly(0, 1);
    }

    // 확인: D1 version, D2 비어 있음·번호 빈틈·순서
    @Test
    void 버전과_번호() {
        assertFails(new DifficultyMasterFile(0, List.of(EASY)), "version");
        assertFails(new DifficultyMasterFile(1, List.of()), "하나도 없습니다");
        assertFails(new DifficultyMasterFile(1, List.of(NORMAL)), "0부터");
        assertFails(new DifficultyMasterFile(1, List.of(EASY, new DifficultyEntry(2, "하드", 1.0, 1.5, 1.3, 1.0, 100))),
                "0부터");
        assertFails(new DifficultyMasterFile(1, List.of(EASY, EASY)), "0부터");
    }

    // 확인: D3 이름, D4 배율 > 0·점수 배수 ≥ 1
    @Test
    void 이름과_배율() {
        assertFails(new DifficultyMasterFile(1, List.of(EASY, new DifficultyEntry(1, " ", 1.0, 1.2, 1.2, 1.0, 100))),
                "name");
        assertFails(new DifficultyMasterFile(1, List.of(EASY, new DifficultyEntry(1, "노말", 0.0, 1.2, 1.2, 1.0, 100))),
                "0보다");
        assertFails(new DifficultyMasterFile(1, List.of(EASY, new DifficultyEntry(1, "노말", 1.0, null, 1.2, 1.0, 100))),
                "0보다");
        assertFails(new DifficultyMasterFile(1, List.of(EASY, new DifficultyEntry(1, "노말", 1.0, 1.2, 1.2, 1.0, 0))),
                "scoreMultiplierPercent");
    }

    // 확인: D5 난이도 0은 조절 없음(배율 1.0, 점수 100)
    @Test
    void 난이도_0은_조절_없음() {
        assertFails(new DifficultyMasterFile(1, List.of(new DifficultyEntry(0, "이지", 1.0, 0.8, 1.0, 1.0, 100))),
                "난이도 0");
        assertFails(new DifficultyMasterFile(1, List.of(new DifficultyEntry(0, "이지", 1.0, 1.0, 1.0, 1.0, 90))),
                "난이도 0");
    }

}
