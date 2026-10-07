package com.pw01.webserver.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회원가입 요청의 칸 검증 규칙(회의 10/7)을 Spring·DB 없이 확인한다.
 * 실패하면 SignupRequest의 정규식·길이 어노테이션과 MaxUtf8Bytes를 의심한다.
 */
class SignupRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    /** 이메일은 선택 칸: 보내지 않으면(null) 통과 */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"user@example.com", "a.b+tag@mail.co.kr", "x_y-z@sub.domain.io"})
    void validEmailPasses(String email) {
        assertThat(emailErrors(email)).isEmpty();
    }

    /** "a@b"(점 없음)와 ""(빈 문자열)는 @Email이 통과시켰던 값이라 여기서 막혀야 한다 */
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a@b", "a@b.", "@example.com", "user@", "user@@example.com", "user example@a.com", "user@example.c"})
    void invalidEmailIsRejected(String email) {
        assertThat(emailErrors(email)).isNotEmpty();
    }

    /** 한글 24자(72바이트)까지는 통과, 25자(75바이트)부터 거절. BCrypt 한도와 같다 */
    @ParameterizedTest
    @ValueSource(ints = {24, 25})
    void passwordByteLimit(int koreanChars) {
        String password = "가".repeat(koreanChars);
        boolean rejected = validator.validate(request("warrior01", password, "용사", null)).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("password"));
        assertThat(rejected).isEqualTo(koreanChars > 24);
    }

    private Set<ConstraintViolation<SignupRequest>> emailErrors(String email) {
        Set<ConstraintViolation<SignupRequest>> all = validator.validate(request("warrior01", "password123", "용사", email));
        all.removeIf(v -> !v.getPropertyPath().toString().equals("email"));
        return all;
    }

    private static SignupRequest request(String loginId, String password, String nickname, String email) {
        return new SignupRequest(loginId, password, nickname, email);
    }

}
