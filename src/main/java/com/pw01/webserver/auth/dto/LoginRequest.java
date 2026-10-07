package com.pw01.webserver.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청. 빈 값만 400으로 거절한다.
 * 아이디 형식 검사는 Service에서 하고, 틀리면 틀린 비밀번호와 같은 401(400으로 거절하면 "그런 아이디는 없다"는 정보가 새므로).
 */
public record LoginRequest(

        @NotBlank(message = "아이디를 입력해야 합니다.")
        String loginId,

        @NotBlank(message = "비밀번호를 입력해야 합니다.")
        String password
) {
}
