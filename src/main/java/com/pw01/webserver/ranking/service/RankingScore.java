package com.pw01.webserver.ranking.service;

import com.pw01.webserver.config.RankingProperties;
import com.pw01.webserver.stage.service.StageCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 스테이지 점수(ranking-api.md "점수"). 정수 계산만 쓴다.
 * <pre>
 * 남은 시간 r = max(0, T − t)        T = 권장 클리어 시간(ms), t = 클리어 시간(ms)
 * 점수 = floor(M × (기본 + floor(시간 점수 최대 × r / T)))     M = 난이도 배수(difficulties.json scoreMultiplierPercent / 100)
 * </pre>
 * 권장 시간을 넘겨도 클리어면 기본 점수(1000)다. 점수는 기록할 때 한 번 계산해 저장하고 다시 계산하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RankingScore {

    private final RankingProperties properties;
    private final StageCatalog stageCatalog;

    public int of(int recommendedClearSeconds, long playTimeMs, int difficulty) {
        if (recommendedClearSeconds < 1 || playTimeMs < 0) {
            throw new IllegalArgumentException("권장 시간은 1초 이상, 클리어 시간은 0 이상이어야 합니다: "
                    + recommendedClearSeconds + "s / " + playTimeMs + "ms");
        }
        long recommendedMs = recommendedClearSeconds * 1000L;
        long remainingMs = Math.max(0, recommendedMs - playTimeMs);
        long base = properties.scoreBase() + properties.scoreTimeWeight() * remainingMs / recommendedMs;
        // 난이도 배수(%)는 difficulties.json(M9). 난이도 0은 늘 100. 보드가 난이도별이라 같은 보드 안 순서는 바뀌지 않는다
        return Math.toIntExact(base * stageCatalog.scoreMultiplierPercent(difficulty) / 100);
    }

}
