package com.pw01.webserver.skill.service;

import com.pw01.webserver.common.master.MasterDataException;
import com.pw01.webserver.skill.master.SkillsFile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * master/skills.json 기동 검사(단위). 틀린 파일이면 서버가 뜨지 않아야 한다.
 * 실패하면 SkillCatalog.validate의 검사 항목(빈 목록·태그·비용·조건 종류·key·목표·선행·순환)을 의심한다.
 */
class SkillCatalogTest {

    /** 판정기 표 대신: 서버가 판정하는 종류 일부 */
    private static final Predicate<String> SUPPORTS = Set.of("STAGES_CLEARED", "TOTAL_KILLS")::contains;

    // 확인: 맞는 파일이면 태그로 찾고, 파일 순서대로 모두 돌려준다
    @Test
    void 태그로_스킬을_찾는다() {
        SkillCatalog catalog = new SkillCatalog(new SkillsFile(List.of(
                skill("Account.Skill.A", 1, List.of(), requirement("STAGES_CLEARED", 1, null)),
                skill("Account.Skill.B", 2, List.of("Account.Skill.A")))), SUPPORTS);

        assertThat(catalog.find("Account.Skill.B")).get().extracting(SkillsFile.Skill::cost).isEqualTo(2);
        assertThat(catalog.find("Account.Skill.Z")).isEmpty();
        assertThat(catalog.all()).extracting(SkillsFile.Skill::tag).containsExactly("Account.Skill.A", "Account.Skill.B");
    }

    // 확인: 빈 목록·빈 태그·28자 태그·비용 음수·중복 태그는 기동 실패
    @Test
    void 틀린_스킬은_기동_실패() {
        assertInvalid(List.of());
        assertInvalid(List.of(skill(" ", 1, List.of())));
        assertInvalid(List.of(skill("Account.Skill.ABCDEFGHIJKLMN", 1, List.of())));
        assertInvalid(List.of(skill("Account.Skill.A", -1, List.of())));
        assertInvalid(List.of(skill("Account.Skill.A", 1, List.of()), skill("Account.Skill.A", 2, List.of())));
    }

    // 확인: 판정하지 않는 조건 종류(KILLS_BY_DEATH_TYPE, 선행·비용을 조건 목록에 씀), key 있는 조건, 목표 음수는 기동 실패
    @Test
    void 판정하지_않는_조건은_기동_실패() {
        assertInvalid(List.of(skill("Account.Skill.A", 1, List.of(), requirement("KILLS_BY_DEATH_TYPE", 3, "Finisher"))));
        assertInvalid(List.of(skill("Account.Skill.A", 1, List.of(), requirement("STAT_POINTS", 1, null))));
        assertInvalid(List.of(skill("Account.Skill.A", 1, List.of(), requirement("TOTAL_KILLS", 3, "BP_SamuraiAI"))));
        assertInvalid(List.of(skill("Account.Skill.A", 1, List.of(), requirement("TOTAL_KILLS", -1, null))));
    }

    // 확인: 없는 선행·겹친 선행·자기 자신·순환(A → B → A)은 기동 실패
    @Test
    void 선행_스킬이_틀리면_기동_실패() {
        assertInvalid(List.of(skill("Account.Skill.A", 1, List.of("Account.Skill.Z"))));
        assertInvalid(List.of(skill("Account.Skill.A", 1, List.of()),
                skill("Account.Skill.B", 1, List.of("Account.Skill.A", "Account.Skill.A"))));
        assertInvalid(List.of(skill("Account.Skill.A", 1, List.of("Account.Skill.A"))));
        assertInvalid(List.of(skill("Account.Skill.A", 1, List.of("Account.Skill.B")),
                skill("Account.Skill.B", 1, List.of("Account.Skill.A"))));
    }

    // 확인: 27자 태그는 받는다(변경 내역 source_id 64자 = requestId 36 + ":" + 27)
    @Test
    void 태그는_27자까지() {
        String tag = "Account.Skill.ABCDEFGHIJKLM";
        assertThat(tag).hasSize(SkillCatalog.TAG_MAX_LENGTH);
        assertThat(new SkillCatalog(new SkillsFile(List.of(skill(tag, 1, List.of()))), SUPPORTS).find(tag)).isPresent();
    }

    private static void assertInvalid(List<SkillsFile.Skill> skills) {
        assertThatThrownBy(() -> new SkillCatalog(new SkillsFile(skills), SUPPORTS))
                .isInstanceOf(MasterDataException.class);
    }

    private static SkillsFile.Skill skill(String tag, int cost, List<String> prerequisites,
                                          SkillsFile.Requirement... requirements) {
        return new SkillsFile.Skill(tag, cost, prerequisites, List.of(requirements));
    }

    private static SkillsFile.Requirement requirement(String type, double target, String key) {
        return new SkillsFile.Requirement(type, target, key);
    }

}
