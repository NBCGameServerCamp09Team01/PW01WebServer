package com.pw01.webserver.auth.controller;

import com.pw01.webserver.auth.dto.AuthenticatedSession;
import com.pw01.webserver.auth.dto.HeartbeatResponse;
import com.pw01.webserver.auth.dto.LoginRequest;
import com.pw01.webserver.auth.dto.LoginResponse;
import com.pw01.webserver.auth.dto.SignupRequest;
import com.pw01.webserver.auth.dto.SignupResponse;
import com.pw01.webserver.auth.interceptor.LoginAccount;
import com.pw01.webserver.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request){
        SignupResponse created = authService.signup(request);
        return ResponseEntity.created(URI.create("/accounts/me")).body(created);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** A4 접속 점검(임시). 연장은 인터셉터가 이미 했으므로 늘어난 만료 시각만 돌려준다 */
    @PostMapping("/heartbeat")
    public HeartbeatResponse heartbeat(@LoginAccount AuthenticatedSession session) {
        return HeartbeatResponse.from(session);
    }

    /** A5 로그아웃. 성공하면 204(본문 없음) */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@LoginAccount AuthenticatedSession session) {
        authService.logout(session);
        return ResponseEntity.noContent().build();
    }
}
