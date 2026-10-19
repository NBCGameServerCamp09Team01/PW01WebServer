package com.pw01.webserver.account.service;

import com.pw01.webserver.account.master.LevelCurveFile;
import com.pw01.webserver.common.master.MasterDataException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 레벨 곡선 단위 시험(서버 판정 = 필수 시험). 파일·DB 없이 값으로 만든다.
 * 서버는 총 경험치에서 레벨을 다시 계산하고(K8-1d), 결과는 UE GrantExperience(지금 경험치에 더해 가며 올림)와 같아야 한다(K8-2a).
 * 실패하면 LevelCurve.gain의 반복 조건(≥)과 최대 레벨 처리, level-curve.json 값을 의심한다.
 */
class LevelCurveTest {

    /** UE FRules와 같은 값(master/level-curve.json) */
    private static final LevelCurveFile UE_RULES = new LevelCurveFile(99, 300, 60, 2, 100, 50);

    private final LevelCurve curve = new LevelCurve(UE_RULES);

    // 확인: UE FRules 주석의 시연 값 — 새 계정 첫 클리어 Lv1 → 3(+4), 두 번째 클리어 Lv3 → 4(+2)
    @Test
    void 시연_값과_같다() {
        LevelGain first = curve.gain(1, 0, curve.resultExp(true));
        assertThat(first).isEqualTo(new LevelGain(300, 1, 3, 50, 300, 4));

        LevelGain second = curve.gain(3, 300, curve.resultExp(true));
        assertThat(second).isEqualTo(new LevelGain(300, 3, 4, 150, 600, 2));
    }

    // 확인: 레벨 경계 — 필요 경험치를 정확히 채우면 오르고(≥), 1 모자라면 안 오른다
    @ParameterizedTest(name = "총 {0} → Lv{1}, 경험치 {2}")
    @CsvSource({"0,1,0", "99,1,99", "100,2,0", "249,2,149", "250,3,0", "449,3,199", "450,4,0"})
    void 레벨_경계(long total, int level, int experience) {
        LevelGain gain = curve.gain(1, 0, (int) total);
        assertThat(gain.levelAfter()).isEqualTo(level);
        assertThat(gain.experienceAfter()).isEqualTo(experience);
        assertThat(gain.statPointsGained()).isEqualTo((level - 1) * 2);
    }

    // 확인: 실패 경험치 60, 레벨이 안 오르면 포인트 0
    @Test
    void 실패는_60() {
        assertThat(curve.gain(1, 0, curve.resultExp(false))).isEqualTo(new LevelGain(60, 1, 1, 60, 60, 0));
    }

    // 확인: 최대 레벨에서는 지금 레벨 안의 경험치 0, 더 오르지 않음(UE와 같음)
    @Test
    void 최대_레벨() {
        LevelCurve small = new LevelCurve(new LevelCurveFile(3, 300, 60, 2, 100, 50));
        LevelGain gain = small.gain(1, 0, 10_000);
        assertThat(gain.levelAfter()).isEqualTo(3);
        assertThat(gain.experienceAfter()).isZero();
        assertThat(gain.statPointsGained()).isEqualTo(4);
        assertThat(small.expToNext(3)).isZero();
    }

    // 확인: 무작위 결과 500개를 이어서 받아도 UE 방식(지금 경험치에 더해 가며 올림)과 레벨·경험치·포인트가 같다
    @Test
    void UE_방식과_같다() {
        Random random = new Random(20261008);
        int level = 1;
        long total = 0;
        int ueLevel = 1;
        int ueExperience = 0;
        int ueStatPoints = 0;
        int statPoints = 0;
        for (int i = 0; i < 500; i++) {
            int exp = curve.resultExp(random.nextBoolean());

            LevelGain gain = curve.gain(level, total, exp);
            level = gain.levelAfter();
            total = gain.totalAfter();
            statPoints += gain.statPointsGained();

            // UE UWarriorAccountSubsystem::GrantExperience를 그대로 옮김
            ueExperience += exp;
            while (ueLevel < 99 && ueExperience >= 100 + (ueLevel - 1) * 50) {
                ueExperience -= 100 + (ueLevel - 1) * 50;
                ueLevel++;
                ueStatPoints += 2;
            }
            if (ueLevel >= 99) {
                ueExperience = 0;
            }

            assertThat(level).as("결과 %d번째 레벨", i).isEqualTo(ueLevel);
            assertThat(gain.experienceAfter()).as("결과 %d번째 경험치", i).isEqualTo(ueExperience);
            assertThat(statPoints).as("결과 %d번째 포인트", i).isEqualTo(ueStatPoints);
        }
    }

    // 확인: 틀린 값(빠진 칸은 0으로 들어옴)이면 기동 실패(MasterDataException)
    @Test
    void 틀린_값은_기동_실패() {
        assertThatThrownBy(() -> new LevelCurve(new LevelCurveFile(0, 300, 60, 2, 100, 50)))
                .isInstanceOf(MasterDataException.class).hasMessageContaining("level-curve.json");
        assertThatThrownBy(() -> new LevelCurve(new LevelCurveFile(99, 0, 60, 2, 100, 50)))
                .isInstanceOf(MasterDataException.class);
        assertThatThrownBy(() -> new LevelCurve(new LevelCurveFile(99, 300, 60, 0, 100, 50)))
                .isInstanceOf(MasterDataException.class);
        assertThatThrownBy(() -> new LevelCurve(new LevelCurveFile(99, 300, 60, 2, 0, 50)))
                .isInstanceOf(MasterDataException.class);
        assertThatThrownBy(() -> new LevelCurve(new LevelCurveFile(99, 50, 60, 2, 100, 50)))
                .isInstanceOf(MasterDataException.class).hasMessageContaining("failedExp");
    }

}
