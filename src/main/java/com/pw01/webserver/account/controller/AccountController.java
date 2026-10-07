package com.pw01.webserver.account.controller;

import com.pw01.webserver.account.dto.AccountSnapshotResponse;
import com.pw01.webserver.account.service.AccountService;
import com.pw01.webserver.auth.interceptor.LoginAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계정 API. /accounts/** 는 모두 인증 인터셉터를 거친다(WebConfig).
 */
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /** A3 메인화면 값: 로그인 응답의 account와 같은 모양. 메인메뉴로 돌아올 때 다시 받는다 */
    @GetMapping("/me")
    public AccountSnapshotResponse me(@LoginAccount Long accountId) {
        return accountService.getSnapshot(accountId);
    }

}
