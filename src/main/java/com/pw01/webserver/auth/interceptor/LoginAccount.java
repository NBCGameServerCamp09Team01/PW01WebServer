package com.pw01.webserver.auth.interceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 인자에 로그인한 계정을 넣는다. 인증 인터셉터가 걸린 경로에서만 쓴다.
 * 타입은 Long(계정 ID) 또는 AuthenticatedSession(토큰·계정 ID·만료 시각).
 * 예: {@code public AccountSnapshotResponse me(@LoginAccount Long accountId)}
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginAccount {
}
