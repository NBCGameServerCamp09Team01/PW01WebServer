package com.pw01.webserver.stageresult.service;

import com.pw01.webserver.common.error.CommonErrorCode;
import com.pw01.webserver.common.error.ConflictException;
import com.pw01.webserver.common.error.ErrorResponse.FieldErrorDetail;
import com.pw01.webserver.common.error.InvalidRequestException;
import com.pw01.webserver.common.error.NotFoundException;

import java.util.List;

/**
 * 결과 오류 코드와 예외(결과 내용 검사·저장 충돌·결과 없음). 플레이를 찾지 못함·만료·끝남은 StagePlayErrors가 맡는다.
 * 오류마다 HTTP 상태(예외 클래스)를 이 파일에서만 정한다(StageErrors·StagePlayErrors와 같은 결).
 */
public final class StageResultErrors {

    public static final String RESULT_MISMATCH = "RESULT_MISMATCH";
    public static final String RESULT_INVALID = "RESULT_INVALID";
    public static final String STAGE_RESULT_NOT_FOUND = "STAGE_RESULT_NOT_FOUND";

    private StageResultErrors() {
    }

    /** 404: 내 플레이는 있는데 결과가 아직 없음(제출 전, 포기·만료로 끝남) */
    public static NotFoundException resultNotFound() {
        return new NotFoundException(STAGE_RESULT_NOT_FOUND, "이 스테이지 플레이의 결과가 없습니다.");
    }

    /** 400: 플레이 시작 때와 스테이지·난이도가 다름 */
    public static InvalidRequestException mismatch(List<FieldErrorDetail> errors) {
        return new InvalidRequestException(RESULT_MISMATCH, "스테이지 플레이 시작 때의 스테이지·난이도와 다릅니다.", errors);
    }

    /** 400: 타당성 검사 실패(웨이브·시간) */
    public static InvalidRequestException invalid(String field, String message) {
        return new InvalidRequestException(RESULT_INVALID, "결과를 확인할 수 없습니다.",
                List.of(new FieldErrorDetail(field, message)));
    }

    /** 409: 같은 계정의 저장이 계속 부딪힘. 다시 보내면 될 수 있으므로 retryable true */
    public static ConflictException versionConflict() {
        return new ConflictException(CommonErrorCode.VERSION_CONFLICT,
                "요청이 몰려 저장하지 못했습니다. 잠시 뒤 다시 보내 주세요.", true);
    }

}
