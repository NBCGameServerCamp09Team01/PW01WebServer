package com.pw01.webserver.auth.dto;

import com.pw01.webserver.account.entity.Account;

import java.time.Instant;

public record SignupResponse(String accountId, String loginId, String nickname, Instant createdAt) {

    public static SignupResponse from(Account account){
        return new SignupResponse(
                String.valueOf(account.getId()),
                account.getLoginId(),
                account.getNickname(),
                account.getCreatedAt()
        );
    }
}
