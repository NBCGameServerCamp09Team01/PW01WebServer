package com.pw01.webserver.stage.master;

import java.util.List;

/**
 * master/stages.json의 모양. 지역 → 스테이지.
 * 키(regionId·stageId)는 UE FName과 같은 문자열이고 바꾸지 않는다(진행·결과 기록이 이 값을 가진다).
 * 웨이브 수의 원본은 UE 웨이브 DataAsset이고 waveCount는 손으로 옮긴 사본이다(기술부채).
 *
 * @param regions 지역 목록. 화면 순서는 order로 정한다(파일 순서와 무관)
 */
public record StageMasterFile(List<RegionEntry> regions) {

    /**
     * @param regionId 지역 키. 예: region.01
     * @param name     화면 이름
     * @param order    지역끼리의 화면 순서. 1부터
     * @param stages   그 지역의 스테이지
     */
    public record RegionEntry(String regionId, String name, int order, List<StageEntry> stages) {
    }

    /**
     * @param stageId   스테이지 키. 예: stage.01.01. 키를 잘라 번호를 읽지 않는다(순서는 order)
     * @param name      화면 이름
     * @param order     지역 안 화면 순서. 1부터
     * @param requires  이 스테이지를 클리어하면 열림. 첫 스테이지는 null
     * @param waveCount 웨이브 수. S2 결과 검사(도달 웨이브)에 쓴다
     */
    public record StageEntry(String stageId, String name, int order, String requires, int waveCount) {
    }

}
