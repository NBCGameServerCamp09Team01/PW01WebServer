package com.pw01.webserver.skill.service;

import com.pw01.webserver.common.error.ConflictException;
import com.pw01.webserver.common.error.ErrorResponse.FieldErrorDetail;
import com.pw01.webserver.common.error.InvalidRequestException;
import com.pw01.webserver.progression.requirement.RequirementProgress;

import java.util.List;
import java.util.Map;

/**
 * 스킬 해금 오류 코드와 예외(루트 docs/contracts/skill-api.md "오류").
 * 오류마다 HTTP 상태(예외 클래스)를 이 파일에서만 정한다(StatErrors와 같은 결).
 */
public final class SkillErrors {

    public static final String SKILL_UNKNOWN = "SKILL_UNKNOWN";
    public static final String SKILL_ALREADY_UNLOCKED = "SKILL_ALREADY_UNLOCKED";
    public static final String SKILL_REQUIREMENTS_NOT_MET = "SKILL_REQUIREMENTS_NOT_MET";
    /** 조건 미충족 오류 본문의 기능별 추가 칸 이름 */
    static final String REQUIREMENTS = "requirements";

    private SkillErrors() {
    }

    /** 400: 해금할 수 없는 스킬 태그(errors에 skillTag) */
    public static InvalidRequestException unknown(String skillTag) {
        return new InvalidRequestException(SKILL_UNKNOWN, "알 수 없는 스킬입니다.",
                List.of(new FieldErrorDetail("skillTag", skillTag + "은(는) 해금할 수 있는 스킬이 아닙니다.")));
    }

    /** 409: 다른 요청으로 이미 해금한 스킬 */
    public static ConflictException alreadyUnlocked() {
        return new ConflictException(SKILL_ALREADY_UNLOCKED, "이미 해금한 스킬입니다.");
    }

    /** 409: 선행·포인트·누적 기록 중 하나라도 미충족. 본문에 조건 전부(requirements)를 싣는다 */
    public static ConflictException requirementsNotMet(List<RequirementProgress> requirements) {
        return new ConflictException(SKILL_REQUIREMENTS_NOT_MET, "해금 조건을 채우지 못했습니다.",
                Map.of(REQUIREMENTS, List.copyOf(requirements)));
    }

}
