package com.pw01.webserver.stat.controller;

import com.pw01.webserver.auth.interceptor.LoginAccount;
import com.pw01.webserver.stat.dto.StatInvestRequest;
import com.pw01.webserver.stat.dto.StatInvestResponse;
import com.pw01.webserver.stat.service.StatInvestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 스탯 투자 API(루트 docs/contracts/stat-api.md). 인증 필요(WebConfig "/**") */
@RestController
@RequestMapping("/accounts/me/stat-investments")
@RequiredArgsConstructor
public class StatInvestController {

    private final StatInvestService statInvestService;

    /** 묶음 투자(올림·내림). 전부 반영 또는 전부 거절. 같은 requestId 재전송은 처음 응답 */
    @PostMapping
    public StatInvestResponse invest(@LoginAccount Long accountId, @Valid @RequestBody StatInvestRequest request) {
        return statInvestService.invest(accountId, request);
    }

}
