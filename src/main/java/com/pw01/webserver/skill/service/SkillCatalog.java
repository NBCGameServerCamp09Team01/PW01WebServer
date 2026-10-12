package com.pw01.webserver.skill.service;

import com.pw01.webserver.common.master.MasterDataException;
import com.pw01.webserver.common.master.MasterDataLoader;
import com.pw01.webserver.progression.requirement.RequirementEvaluatorRegistry;
import com.pw01.webserver.skill.master.SkillsFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 해금할 수 있는 스킬: master/skills.json을 기동 때 읽고 검사해 들고 있다(틀리면 기동 실패).
 * 스킬을 더할 때는 파일 한 줄(+ UE DA_SkillTree)이면 되고 이 클래스는 바꾸지 않는다. 값은 기동 뒤 바뀌지 않는다.
 * 서버가 판정하지 않는 조건 종류(판정기 표에 없음)를 쓰면 기동 실패다(루트 docs/contracts/skill-api.md "조건 종류").
 */
@Component
public class SkillCatalog {

    static final String FILE_NAME = "skills.json";
    /**
     * 태그 최대 길이. 변경 내역 source_id(VARCHAR 64) = requestId 36자 + ":" + 태그이므로 27자까지(AccountLedger.skillUnlock)
     */
    static final int TAG_MAX_LENGTH = 27;

    private final List<SkillsFile.Skill> skills;
    private final Map<String, SkillsFile.Skill> byTag;

    @Autowired
    public SkillCatalog(MasterDataLoader loader, RequirementEvaluatorRegistry registry) {
        this(loader.load(FILE_NAME, SkillsFile.class), registry::supports);
    }

    /** 시험용: 파일 없이 값으로 만든다(검사는 같다). supports = 판정할 수 있는 조건 종류인가 */
    SkillCatalog(SkillsFile file, Predicate<String> supports) {
        this.byTag = validate(file, supports);
        this.skills = List.copyOf(byTag.values());
    }

    /** 스킬 정의. 없는 스킬이면 빈 값 */
    public Optional<SkillsFile.Skill> find(String tag) {
        return Optional.ofNullable(byTag.get(tag));
    }

    /** 모든 스킬(파일 순서) */
    public List<SkillsFile.Skill> all() {
        return skills;
    }

    private static Map<String, SkillsFile.Skill> validate(SkillsFile file, Predicate<String> supports) {
        if (file.skills() == null || file.skills().isEmpty()) {
            throw fail("skills가 비어 있습니다.");
        }
        Map<String, SkillsFile.Skill> map = new LinkedHashMap<>();
        for (SkillsFile.Skill skill : file.skills()) {
            if (skill.tag() == null || skill.tag().isBlank()) {
                throw fail("tag가 비어 있는 스킬이 있습니다.");
            }
            if (skill.tag().length() > TAG_MAX_LENGTH) {
                throw fail("tag는 " + TAG_MAX_LENGTH + "자 이하여야 합니다(변경 내역 source_id 길이): " + skill.tag());
            }
            if (skill.cost() < 0) {
                throw fail("cost는 0 이상이어야 합니다: " + skill.tag());
            }
            for (SkillsFile.Requirement requirement : skill.requirements()) {
                if (requirement.type() == null || !supports.test(requirement.type())) {
                    throw fail("서버가 판정하지 않는 조건 종류입니다: " + skill.tag() + " " + requirement.type());
                }
                if (requirement.key() != null) {
                    throw fail("key가 있는 조건(적 종류별 처치 등)은 판정하지 않습니다: " + skill.tag() + " "
                            + requirement.type() + " " + requirement.key());
                }
                if (requirement.target() < 0) {
                    throw fail("target은 0 이상이어야 합니다: " + skill.tag() + " " + requirement.type());
                }
            }
            if (map.put(skill.tag(), skill) != null) {
                throw fail("tag가 겹칩니다: " + skill.tag());
            }
        }
        for (SkillsFile.Skill skill : map.values()) {
            Set<String> seen = new HashSet<>();
            for (String prerequisite : skill.prerequisites()) {
                if (!map.containsKey(prerequisite)) {
                    throw fail("없는 선행 스킬입니다: " + skill.tag() + " → " + prerequisite);
                }
                if (!seen.add(prerequisite)) {
                    throw fail("선행 스킬이 겹칩니다: " + skill.tag() + " → " + prerequisite);
                }
            }
        }
        for (String tag : map.keySet()) {
            checkNoCycle(tag, map, new HashSet<>(), new HashSet<>());
        }
        return map;
    }

    /** 선행 스킬을 따라가다 같은 스킬로 돌아오면(자기 자신 포함) 해금할 수 없는 스킬이 생기므로 기동 실패 */
    private static void checkNoCycle(String tag, Map<String, SkillsFile.Skill> map, Set<String> path, Set<String> done) {
        if (done.contains(tag)) {
            return;
        }
        if (!path.add(tag)) {
            throw fail("선행 스킬이 돌고 돕니다: " + String.join(" → ", path) + " → " + tag);
        }
        for (String prerequisite : map.get(tag).prerequisites()) {
            checkNoCycle(prerequisite, map, path, done);
        }
        path.remove(tag);
        done.add(tag);
    }

    private static MasterDataException fail(String message) {
        return new MasterDataException(FILE_NAME, message);
    }

}
