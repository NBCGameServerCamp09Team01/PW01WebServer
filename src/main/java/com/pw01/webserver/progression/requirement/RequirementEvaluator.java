package com.pw01.webserver.progression.requirement;

/**
 * 조건 종류 하나의 판정. 빈으로 등록하면 RequirementEvaluatorRegistry가 모은다.
 * 조건 하나 더하기 = 이 인터페이스 구현 하나(재료가 없으면 누적 통계 키 하나도). 서비스·컨트롤러는 바꾸지 않는다.
 */
public interface RequirementEvaluator {

    /** 조건 종류 이름(RequirementTypes) */
    String type();

    /** facts로 target을 판정한다 */
    RequirementProgress evaluate(AccountFacts facts, double target);

}
