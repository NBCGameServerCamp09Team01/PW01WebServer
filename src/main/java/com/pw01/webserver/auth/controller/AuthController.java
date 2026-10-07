package com.pw01.webserver.auth.controller;

import com.pw01.webserver.auth.dto.SignupRequest;
import com.pw01.webserver.auth.dto.SignupResponse;
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
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request){
        SignupResponse created = authService.signup(request);
        return ResponseEntity.created(URI.create("/api/accounts/me")).body(created);
    }
}
