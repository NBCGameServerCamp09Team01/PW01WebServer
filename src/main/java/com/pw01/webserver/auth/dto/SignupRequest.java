package com.pw01.webserver.auth.dto;

import com.pw01.webserver.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "아이디를 입력해야 합니다.")
        @Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "아이디는 영문·숫자 4~20자여야 합니다.")
        String loginId,

        @NotBlank(message = "비밀번호를 입력해야 합니다.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        @MaxUtf8Bytes(value = 72, message = "비밀번호가 너무 깁니다.")
        String password,

        @NotBlank(message = "닉네임을 입력해야 합니다.")
        @Pattern(regexp = "^[가-힣A-Za-z0-9][가-힣A-Za-z0-9 ]{0,18}[가-힣A-Za-z0-9]$",
                message = "닉네임은 한글·영문·숫자 2~20자이고 앞뒤에 공백을 쓸 수 없습니다.")
        String nickname,

        // @Email은 "a@b"(점 없음)와 ""(빈 문자열)를 통과시켜서 쓰지 않는다. 선택 칸이라 null만 "안 보냄"으로 받는다
        @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$",
                message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
        String email
){
}
