package com.pw01.webserver.auth.repository;

/**
 * 로그인 세션 저장소(Redis). 메서드 이름은 part d 회신 2-1을 따른다.
 * c(서비스)가 이 인터페이스를 쓰고, d가 Redis 구현체(키 2개 + Lua)를 만든다.
 * 키·스크립트·명령 순서는 루트 docs/contracts/redis-keys.md v1, 세션 수명은 AuthProperties(pw01.auth.session.ttl).
 * 토큰 원문은 create만 돌려주고 저장소 안에서는 해시로만 다룬다.
 */
public interface SessionStore {

    /** 새 세션을 만든다(session_login.lua). 같은 계정의 이전 세션은 지우지 않고 "다른 곳에서 로그인" 상태가 된다 */
    IssuedSession create(Long accountId);

    /** 인증이 필요한 요청마다: 세션 키 GET → 있으면 session_extend.lua로 두 키를 함께 연장한다 */
    SessionCheck touch(String token);

    /** 로그아웃(session_logout.lua): 내 세션 키는 지우고, 계정 키는 지금 값이 내 해시일 때만 지운다 */
    void delete(String token, Long accountId);

}
