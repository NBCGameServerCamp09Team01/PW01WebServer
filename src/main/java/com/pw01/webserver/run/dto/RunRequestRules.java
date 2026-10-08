package com.pw01.webserver.run.dto;

/** 판·결과 요청이 같이 쓰는 모양 규칙 */
public final class RunRequestRules {

    /** UUID 하이픈 36자(대소문자 모두 받고 서버가 소문자로 맞춘다) */
    public static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private RunRequestRules() {
    }

}
