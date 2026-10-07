package com.pw01.webserver.auth.repository;

/** 실패 기록 결과: 지금까지 실패 횟수와 남은 잠김 시간(초, 잠기지 않았으면 0) */
public record LoginFailResult(long count, long lockRemainingSeconds) {

    public boolean locked() {
        return lockRemainingSeconds > 0;
    }

}
