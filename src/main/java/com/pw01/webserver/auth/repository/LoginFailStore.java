package com.pw01.webserver.auth.repository;

/**
 * 로그인 실패 횟수 저장소(Redis). 메서드 이름은 part d 회신 2-1을 따른다.
 * 키는 루트 docs/contracts/redis-keys.md v1의 pw01:login-fail:<아이디>. 아이디는 입력 그대로 쓴다
 * (아이디가 대소문자를 구분하므로 소문자로 맞추면 다른 계정끼리 잠김을 같이 쓴다).
 * 허용 횟수·세는 시간·잠김 시간은 AuthProperties(pw01.auth.login-fail.*)에서 읽는다.
 */
public interface LoginFailStore {

    /** 남은 잠김 시간(초). 잠기지 않았으면 0 */
    long lockRemaining(String loginId);

    /** 실패 1회를 기록한다. 이번 실패로 잠기면 남은 잠김 초가 0보다 크다 */
    LoginFailResult recordFailure(String loginId);

    /** 로그인에 성공하면 실패 기록을 지운다 */
    void clear(String loginId);

}
