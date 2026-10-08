package com.pw01.webserver.stageplay.service;

import com.pw01.webserver.common.error.CommonErrorCode;
import com.pw01.webserver.common.error.ConflictException;
import com.pw01.webserver.common.error.ErrorResponse.FieldErrorDetail;
import com.pw01.webserver.common.error.InvalidRequestException;
import com.pw01.webserver.common.error.NotFoundException;

import java.util.List;

/**
 * 스테이지 플레이 오류 코드와 예외(공유 초안 stage-play-api-draft-1009.md 6장).
 * 오류마다 HTTP 상태(예외 클래스)를 이 파일에서만 정한다(StageErrors·RunErrors와 같은 결).
 */
public final class StagePlayErrors {

    public static final String STAGE_PLAY_NOT_FOUND = "STAGE_PLAY_NOT_FOUND";
    public static final String STAGE_PLAY_IN_PROGRESS = "STAGE_PLAY_IN_PROGRESS";
    public static final String STAGE_PLAY_NOT_IN_PROGRESS = "STAGE_PLAY_NOT_IN_PROGRESS";

    private StagePlayErrors() {
    }

    /** 404: 없는 플레이, 남의 플레이(있는지 알려 주지 않음) */
    public static NotFoundException notFound() {
        return new NotFoundException(STAGE_PLAY_NOT_FOUND, "스테이지 플레이를 찾을 수 없습니다.");
    }

    /** 409: 진행 중인 플레이가 있어 새로 시작할 수 없음. 게임은 current로 받아 포기(나중에는 이어하기)한다 */
    public static ConflictException inProgress() {
        return new ConflictException(STAGE_PLAY_IN_PROGRESS, "진행 중인 스테이지 플레이가 있습니다.");
    }

    /** 409: 이미 끝난 플레이(결과·포기·만료)에 포기·결과를 보냄 */
    public static ConflictException notInProgress() {
        return new ConflictException(STAGE_PLAY_NOT_IN_PROGRESS, "이미 끝난 스테이지 플레이입니다.");
    }

    /** 409: 같은 requestId로 다른 스테이지를 시작하려 함(게임 버그) */
    public static ConflictException requestIdReused() {
        return new ConflictException(CommonErrorCode.IDEMPOTENCY_KEY_REUSED,
                "같은 requestId로 다른 스테이지를 시작할 수 없습니다.");
    }

    /** 400: 경로의 스테이지 플레이 ID를 UUID로 읽지 못함 */
    public static InvalidRequestException badId() {
        return new InvalidRequestException(CommonErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않습니다.",
                List.of(new FieldErrorDetail("stagePlayId", "스테이지 플레이 ID는 UUID(하이픈 36자)여야 합니다.")));
    }

    /** 400: 목록 조회 status는 지금 IN_PROGRESS만 */
    public static InvalidRequestException badStatusFilter() {
        return new InvalidRequestException(CommonErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않습니다.",
                List.of(new FieldErrorDetail("status", "status는 지금 IN_PROGRESS만 받습니다.")));
    }

}
