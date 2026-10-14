package com.pw01.webserver.stage.service;

import java.util.List;

/**
 * 스테이지 정의 하나(stages.json + stage-waves.json). 다른 기능(S2 판 시작·결과, 랭킹)이 StageCatalog·StageProgressService에서 받는 값이다.
 *
 * @param stageId                 스테이지 키(UE FName과 같은 문자열)
 * @param regionId                속한 지역 키
 * @param name                    화면 이름
 * @param order                   지역 안 화면 순서. 1부터
 * @param requires                이 스테이지를 클리어하면 열림. 첫 스테이지는 null
 * @param waveCount               웨이브 수. 결과 검사는 UE가 보낸 값이 아니라 이 값으로 한다
 * @param recommendedClearSeconds 권장 클리어 시간(초). 랭킹 점수의 기준(ranking-api.md "점수")
 * @param waves                   웨이브 구성(몬스터 값을 풀어 둔 것, 웨이브 번호 순). 수는 waveCount와 같다(기동 검사)
 */
public record StageDef(String stageId, String regionId, String name, int order, String requires, int waveCount,
                       int recommendedClearSeconds, List<WaveDef> waves) {
}
