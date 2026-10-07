package com.pw01.webserver.auth.service;

import com.pw01.webserver.account.entity.Account;
import com.pw01.webserver.account.service.AccountService;
import com.pw01.webserver.auth.dto.SignupRequest;
import com.pw01.webserver.auth.dto.SignupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 회원가입·로그인 흐름(유스케이스). 계정 데이터는 AccountService로만 다룬다.
 * 비밀번호 해시는 느린 계산이라 트랜잭션 밖에서 한다(트랜잭션은 AccountService.register가 가진다).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;

    public SignupResponse signup(SignupRequest request){
        String passwordHash = passwordEncoder.encode(request.password());
        Account account = accountService.register(
                request.loginId(), passwordHash, request.nickname(), request.email());
        return SignupResponse.from(account);
    }
}
