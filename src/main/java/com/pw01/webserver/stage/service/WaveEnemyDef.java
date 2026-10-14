package com.pw01.webserver.stage.service;

/**
 * 웨이브의 적 한 줄. 몬스터 정의 값에 웨이브 파일의 덮어쓰기를 반영한 값이다.
 *
 * @param monsterId      몬스터 키
 * @param count          마리 수
 * @param boss           보스 몬스터
 * @param spawnGroup     스폰 그룹 이름. 없으면 null(기본 스폰)
 * @param goldReward     한 마리 골드(웨이브 배율을 곱하기 전)
 * @param goldDropChance 골드를 떨굴 확률(웨이브 배율을 곱하기 전)
 */
public record WaveEnemyDef(String monsterId, int count, boolean boss, String spawnGroup, int goldReward,
                           double goldDropChance) {
}
