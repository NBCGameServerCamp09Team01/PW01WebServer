package com.pw01.webserver.stage.service;

import com.pw01.webserver.common.error.ForbiddenException;
import com.pw01.webserver.common.error.NotFoundException;

/**
 * 스테이지 오류 코드와 예외. 목록은 루트 docs/contracts/stage-api.md "오류"가 기준이다.
 * 오류마다 HTTP 상태(예외 클래스)를 이 파일에서만 정한다.
 */
public final class StageErrors {

    /** 없는 stageId(게임·서버 키가 어긋난 버그) */
    public static final String STAGE_NOT_FOUND = "STAGE_NOT_FOUND";

    /** 아직 열리지 않은 스테이지로 판을 시작함 */
    public static final String STAGE_LOCKED = "STAGE_LOCKED";

    private StageErrors() {
    }

    /** 404: 요청 모양은 맞고 그 키가 없다 */
    public static NotFoundException notFound() {
        return new NotFoundException(STAGE_NOT_FOUND, "스테이지 정보를 찾을 수 없습니다.");
    }

    /** 403: 다시 보내도 결과가 같다(409는 다시 보내면 될 수 있는 충돌에 쓴다) */
    public static ForbiddenException locked() {
        return new ForbiddenException(STAGE_LOCKED, "아직 열리지 않은 스테이지입니다.");
    }

}
