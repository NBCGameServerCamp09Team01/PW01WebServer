package com.pw01.webserver.stat.master;

import java.util.List;

/**
 * master/stats.json 모양(스탯 정의). 원본은 UE UWarriorAccountSubsystem::BuildDefinitions()이고 손으로 맞춘다.
 *
 * @param stats 투자할 수 있는 스탯 목록
 */
public record StatsFile(List<Stat> stats) {

    /**
     * @param tag           스탯 태그 = UE 게임플레이 태그 이름(대소문자 구분)
     * @param maxPoints     최대 투자 포인트(1 이상)
     * @param bonusPerPoint 포인트당 증가량. 서버 판정에는 쓰지 않음(최종 능력치는 UE가 계산, 나중 검산용)
     */
    public record Stat(String tag, int maxPoints, double bonusPerPoint) {
    }

}
