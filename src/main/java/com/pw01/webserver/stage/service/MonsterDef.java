package com.pw01.webserver.stage.service;

/**
 * 몬스터 정의 하나(monsters.json). 웨이브의 적 줄이 덮어쓰지 않은 값의 기본이다.
 *
 * @param monsterId      몬스터 키(UE 몬스터 카탈로그의 키와 같은 문자열)
 * @param name           화면 이름(스테이지 선택 미리보기)
 * @param boss           보스 몬스터
 * @param goldReward     한 마리 골드
 * @param goldDropChance 골드를 떨굴 확률(0~1)
 */
public record MonsterDef(String monsterId, String name, boolean boss, int goldReward, double goldDropChance) {
}
