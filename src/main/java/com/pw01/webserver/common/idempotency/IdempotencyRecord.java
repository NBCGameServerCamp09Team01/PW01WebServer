package com.pw01.webserver.common.idempotency;

/**
 * 중복 방지 키(pw01:idem:…)의 값. 토큰·비밀번호는 넣지 않는다.
 *
 * @param state    IN_PROGRESS(처리 중) · DONE(끝남, body가 처음 응답)
 * @param bodyHash 요청 본문(서버가 다시 쓴 JSON)의 SHA-256 소문자 16진수 64자
 * @param body     처음 응답 data의 JSON 문자열. IN_PROGRESS면 null
 */
public record IdempotencyRecord(State state, String bodyHash, String body) {

    public enum State {
        IN_PROGRESS,
        DONE
    }

    static IdempotencyRecord inProgress(String bodyHash) {
        return new IdempotencyRecord(State.IN_PROGRESS, bodyHash, null);
    }

    static IdempotencyRecord done(String bodyHash, String body) {
        return new IdempotencyRecord(State.DONE, bodyHash, body);
    }

}
