package com.pw01.webserver.stage.master;

import java.util.List;

/**
 * master/monsters.json의 모양. 몬스터 정의(stage-api.md "서버 정의 파일").
 * 키(monsterId)는 UE 몬스터 카탈로그의 키와 같은 문자열이고 바꾸지 않는다(진행 중 플레이의 구성이 이 값을 가진다).
 * 칸이 빠지면 검사에서 걸리도록 필수 칸도 상자 타입(null)으로 받는다.
 *
 * @param version  파일 버전(1 이상). 값을 바꾸면 1 올린다
 * @param monsters 몬스터 목록. 이 순서로 ST1 monsters에 나간다
 */
public record MonsterMasterFile(int version, List<MonsterEntry> monsters) {

    /**
     * @param monsterId      몬스터 키. 예: monster.samurai
     * @param name           화면 이름(미리보기)
     * @param boss           보스 몬스터
     * @param goldReward     한 마리 골드(0 이상)
     * @param goldDropChance 골드를 떨굴 확률(0~1)
     */
    public record MonsterEntry(String monsterId, String name, Boolean boss, Integer goldReward,
                               Double goldDropChance) {
    }

}
