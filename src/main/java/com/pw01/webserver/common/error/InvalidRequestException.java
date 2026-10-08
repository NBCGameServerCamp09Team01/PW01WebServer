package com.pw01.webserver.common.error;

import com.pw01.webserver.common.error.ErrorResponse.FieldErrorDetail;
import org.springframework.http.HttpStatus;

import java.util.List;

/** 400: 형식 검증은 통과했지만 Service의 판정에서 받아들일 수 없는 요청 */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }

    /** 어느 칸이 문제인지 errors에 싣는다(예: RESULT_INVALID의 reachedWave) */
    public InvalidRequestException(String code, String message, List<FieldErrorDetail> errors) {
        super(HttpStatus.BAD_REQUEST, code, message, false, errors);
    }

}
