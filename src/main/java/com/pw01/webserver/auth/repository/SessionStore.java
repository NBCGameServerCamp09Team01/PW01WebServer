package com.pw01.webserver.auth.repository;

/**
 * 로그인 세션 저장소(Redis). 메서드 이름은 part d 회신 2-1을 따른다.
 * c(서비스)가 이 인터페이스를 쓰고, d가 Redis 구현체(키 2개 + Lua)를 만든다.
 * 토큰 원문은 create만 돌려주고 저장소 안에서는 해시로만 다룬다.
 */
public interface SessionStore {

    /** 새 세션을 만든다. 같은 계정의 이전 세션은 지우지 않고 "다른 곳에서 로그인" 상태가 된다 */
    IssuedSession create(Long accountId);

}
