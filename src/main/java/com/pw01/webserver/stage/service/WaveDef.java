package com.pw01.webserver.stage.service;

import java.util.List;

/**
 * 웨이브 하나. 기본값과 몬스터 정의 값을 모두 풀어 둔 값이다(stage-play-api.md "waves[]"와 같은 칸).
 * 스테이지 플레이는 시작 때 이 목록을 JSON으로 저장하고(stage_play.wave_plan), 그 판은 끝까지 그 값을 쓴다.
 * 칸 이름을 바꾸면 이미 저장한 플레이를 읽지 못하므로 바꾸지 않는다(명세 응답 칸과 같음).
 *
 * @param waveNumber           웨이브 번호. 1부터
 * @param restSeconds          이 웨이브 앞 쉬는 시간(초). 0이면 게임 기본
 * @param bossWave             보스 웨이브
 * @param goldChanceMultiplier 골드 확률 배율
 * @param goldAmountMultiplier 골드 양 배율
 * @param enemies              나올 적
 */
public record WaveDef(int waveNumber, double restSeconds, boolean bossWave, double goldChanceMultiplier,
                      double goldAmountMultiplier, List<WaveEnemyDef> enemies) {
}
