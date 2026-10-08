package com.pw01.webserver.stage.master;

import com.pw01.webserver.common.master.MasterDataException;
import com.pw01.webserver.stage.master.StageMasterFile.RegionEntry;
import com.pw01.webserver.stage.master.StageMasterFile.StageEntry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * stages.json 검사. 하나라도 걸리면 MasterDataException → 기동 실패.
 * 1 지역·스테이지가 하나 이상 / 2 키 모양·이름 / 3 키 중복 없음 / 4 order ≥ 1, 겹치지 않음
 * 5 waveCount ≥ 1 / 6 requires는 null이거나 있는 다른 스테이지 / 7 requires 순환 없음 / 8 requires가 없는 스테이지가 하나 이상
 */
public final class StageMasterValidator {

    static final String FILE_NAME = "stages.json";

    /** 영문 소문자·숫자·점, 1~64자. UE FName은 대소문자를 가리지 않으므로 소문자만 쓴다(DB CHECK와 같은 규칙) */
    private static final Pattern KEY = Pattern.compile("^[a-z0-9.]{1,64}$");

    private StageMasterValidator() {
    }

    public static void validate(StageMasterFile file) {
        if (file.regions() == null || file.regions().isEmpty()) {
            throw fail("지역이 하나도 없습니다.");
        }

        Set<String> regionIds = new HashSet<>();
        Set<Integer> regionOrders = new HashSet<>();
        Map<String, StageEntry> stages = new HashMap<>();

        for (RegionEntry region : file.regions()) {
            checkKey(region.regionId(), "지역");
            checkName(region.name(), region.regionId());
            if (!regionIds.add(region.regionId())) {
                throw fail("지역 키가 겹칩니다: " + region.regionId());
            }
            checkOrder(region.order(), regionOrders, "지역 " + region.regionId());

            if (region.stages() == null || region.stages().isEmpty()) {
                throw fail("스테이지가 없는 지역입니다: " + region.regionId());
            }
            Set<Integer> stageOrders = new HashSet<>();
            for (StageEntry stage : region.stages()) {
                checkKey(stage.stageId(), "스테이지");
                checkName(stage.name(), stage.stageId());
                if (stages.putIfAbsent(stage.stageId(), stage) != null) {
                    throw fail("스테이지 키가 겹칩니다: " + stage.stageId());
                }
                checkOrder(stage.order(), stageOrders, "스테이지 " + stage.stageId());
                if (stage.waveCount() < 1) {
                    throw fail("waveCount는 1 이상이어야 합니다: " + stage.stageId());
                }
            }
        }

        checkRequires(stages);
    }

    private static void checkRequires(Map<String, StageEntry> stages) {
        boolean hasRoot = false;
        for (StageEntry stage : stages.values()) {
            String requires = stage.requires();
            if (requires == null) {
                hasRoot = true;
                continue;
            }
            if (requires.equals(stage.stageId())) {
                throw fail("requires가 자기 자신입니다: " + stage.stageId());
            }
            if (!stages.containsKey(requires)) {
                throw fail("requires가 없는 스테이지를 가리킵니다: " + stage.stageId() + " → " + requires);
            }
        }
        if (!hasRoot) {
            throw fail("requires가 없는(처음부터 열린) 스테이지가 없습니다.");
        }

        // 스테이지 수만큼 따라가도 끝(null)에 닿지 않으면 순환이다
        for (StageEntry start : stages.values()) {
            String current = start.requires();
            for (int step = 0; current != null; step++) {
                if (step >= stages.size()) {
                    throw fail("requires가 순환합니다: " + start.stageId());
                }
                current = stages.get(current).requires();
            }
        }
    }

    private static void checkKey(String key, String kind) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw fail(kind + " 키 모양이 틀렸습니다(영문 소문자·숫자·점, 1~64자): " + key);
        }
    }

    private static void checkName(String name, String key) {
        if (name == null || name.isBlank()) {
            throw fail("name이 비어 있습니다: " + key);
        }
    }

    private static void checkOrder(int order, Set<Integer> used, String target) {
        if (order < 1) {
            throw fail("order는 1 이상이어야 합니다: " + target);
        }
        if (!used.add(order)) {
            throw fail("order가 겹칩니다: " + target + " (order " + order + ")");
        }
    }

    private static MasterDataException fail(String reason) {
        return new MasterDataException(FILE_NAME, reason);
    }

}
