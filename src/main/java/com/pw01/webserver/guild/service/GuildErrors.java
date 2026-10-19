package com.pw01.webserver.guild.service;

import com.pw01.webserver.common.error.CommonErrorCode;
import com.pw01.webserver.common.error.ConflictException;
import com.pw01.webserver.common.error.ErrorResponse.FieldErrorDetail;
import com.pw01.webserver.common.error.ForbiddenException;
import com.pw01.webserver.common.error.InvalidRequestException;
import com.pw01.webserver.common.error.NotFoundException;

import java.util.List;

/**
 * 길드 오류 코드와 예외. 목록·HTTP 상태는 루트 docs/contracts/guild-api.md "오류"가 기준이다. 모두 retryable false.
 * 409 VERSION_CONFLICT(저장 충돌)는 ProgressWriter가 던지는 공통 코드를 그대로 쓴다.
 */
public final class GuildErrors {

    public static final String GUILD_NOT_FOUND = "GUILD_NOT_FOUND";
    public static final String GUILD_NOT_MEMBER = "GUILD_NOT_MEMBER";
    public static final String GUILD_NOT_LEADER = "GUILD_NOT_LEADER";
    public static final String GUILD_ALREADY_JOINED = "GUILD_ALREADY_JOINED";
    public static final String GUILD_FULL = "GUILD_FULL";
    public static final String GUILD_NAME_DUPLICATED = "GUILD_NAME_DUPLICATED";
    public static final String GUILD_LIMIT_REACHED = "GUILD_LIMIT_REACHED";
    public static final String GUILD_CREATE_REQUIREMENTS_NOT_MET = "GUILD_CREATE_REQUIREMENTS_NOT_MET";
    public static final String GUILD_JOIN_REQUEST_DUPLICATED = "GUILD_JOIN_REQUEST_DUPLICATED";
    public static final String GUILD_JOIN_REQUEST_LIMIT = "GUILD_JOIN_REQUEST_LIMIT";
    public static final String GUILD_JOIN_REQUEST_NOT_FOUND = "GUILD_JOIN_REQUEST_NOT_FOUND";
    public static final String GUILD_MEMBER_NOT_FOUND = "GUILD_MEMBER_NOT_FOUND";
    public static final String GUILD_LEADER_CANNOT_LEAVE = "GUILD_LEADER_CANNOT_LEAVE";

    private GuildErrors() {
    }

    /** 404: 없는 길드(해체됨 포함) */
    public static NotFoundException notFound() {
        return new NotFoundException(GUILD_NOT_FOUND, "길드를 찾을 수 없습니다.");
    }

    /** 403: 길드가 없는 계정이 내 길드 기능을 부름 */
    public static ForbiddenException notMember() {
        return new ForbiddenException(GUILD_NOT_MEMBER, "가입한 길드가 없습니다.");
    }

    /** 403: 길드장 기능을 길드원이 부름 */
    public static ForbiddenException notLeader() {
        return new ForbiddenException(GUILD_NOT_LEADER, "길드장만 할 수 있습니다.");
    }

    /** 409: 이미 길드가 있음(만들기·신청), 또는 수락하는 순간 신청자가 다른 길드에 들어감 */
    public static ConflictException alreadyJoined() {
        return new ConflictException(GUILD_ALREADY_JOINED, "이미 길드에 가입했습니다.");
    }

    /** 409: 정원 */
    public static ConflictException full() {
        return new ConflictException(GUILD_FULL, "길드 정원이 찼습니다.");
    }

    /** 409: 같은 이름(대소문자 구분) */
    public static ConflictException nameDuplicated() {
        return new ConflictException(GUILD_NAME_DUPLICATED, "이미 있는 길드 이름입니다.");
    }

    /** 409: 서버 길드 수 상한 */
    public static ConflictException limitReached() {
        return new ConflictException(GUILD_LIMIT_REACHED, "더 이상 길드를 만들 수 없습니다.");
    }

    /** 409: 같은 길드에 대기 중 신청이 있음 */
    public static ConflictException joinRequestDuplicated() {
        return new ConflictException(GUILD_JOIN_REQUEST_DUPLICATED, "이미 신청한 길드입니다.");
    }

    /** 409: 대기 중 신청 수 상한 */
    public static ConflictException joinRequestLimit(int max) {
        return new ConflictException(GUILD_JOIN_REQUEST_LIMIT, "가입 신청은 " + max + "곳까지 할 수 있습니다.");
    }

    /** 404: 수락·거절할 신청이 없음(취소·만료·처리됨) */
    public static NotFoundException joinRequestNotFound() {
        return new NotFoundException(GUILD_JOIN_REQUEST_NOT_FOUND, "가입 신청을 찾을 수 없습니다.");
    }

    /** 404: 추방할 계정이 내 길드원이 아님(Lv2) */
    public static NotFoundException memberNotFound() {
        return new NotFoundException(GUILD_MEMBER_NOT_FOUND, "길드원을 찾을 수 없습니다.");
    }

    /** 409: 길드원이 있는 길드장의 탈퇴 */
    public static ConflictException leaderCannotLeave() {
        return new ConflictException(GUILD_LEADER_CANNOT_LEAVE, "길드원이 있으면 길드장은 탈퇴할 수 없습니다.");
    }

    /** 400 VALIDATION_FAILED: 경로·쿼리 값(guildId·name·cursor·size)이 틀림. 문구는 공통 검증 오류와 같다 */
    public static InvalidRequestException invalidValue(String field, String message) {
        return new InvalidRequestException(CommonErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않습니다.",
                List.of(new FieldErrorDetail(field, message)));
    }

}
