package com.pw01.webserver.stage.dto;

import com.pw01.webserver.stage.service.MonsterDef;

/** ST1 몬스터 정의 하나(stage-api.md v1.3). 미리보기의 이름에 쓴다. 골드·확률은 싣지 않는다 */
public record MonsterResponse(String monsterId, String name, boolean boss) {

    public static MonsterResponse from(MonsterDef monster) {
        return new MonsterResponse(monster.monsterId(), monster.name(), monster.boss());
    }

}
