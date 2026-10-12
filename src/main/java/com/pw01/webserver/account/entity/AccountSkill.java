package com.pw01.webserver.account.entity;

import com.pw01.webserver.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 계정이 해금한 스킬 한 줄(account_skill, S4). 기본 키 = 계정 + 스킬 태그 = 같은 스킬은 한 번만.
 * 넣기만 하고 고치지 않는다(스킬 초기화는 아직 없음). 버전은 두지 않고, 해금 때 진행 행 version이 같이 오른다.
 */
@Entity
@Table(name = "account_skill")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountSkill extends BaseEntity {

    @EmbeddedId
    private AccountSkillId id;

    /** 해금한 요청 번호. 같은 요청이 다시 오면 ALREADY_SAVED, 다른 요청이면 SKILL_ALREADY_UNLOCKED로 가른다 */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 36)
    private String requestId;

    public static AccountSkill unlocked(Long accountId, String skillTag, String requestId) {
        AccountSkill skill = new AccountSkill();
        skill.id = new AccountSkillId(accountId, skillTag);
        skill.requestId = requestId;
        return skill;
    }

    public String getSkillTag() {
        return id.getSkillTag();
    }

}
