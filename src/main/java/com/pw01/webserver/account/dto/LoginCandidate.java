package com.pw01.webserver.account.dto;

import com.pw01.webserver.account.entity.AccountStatus;

/**
 * 로그인 판단에 필요한 서버 내부 값. auth가 비밀번호·상태를 볼 때만 쓰고 API로 내보내지 않는다.
 * Account 엔티티를 기능(account) 밖으로 넘기지 않으려고 필요한 세 값만 담는다.
 */
public record LoginCandidate(Long accountId, String passwordHash, AccountStatus status) {
}
