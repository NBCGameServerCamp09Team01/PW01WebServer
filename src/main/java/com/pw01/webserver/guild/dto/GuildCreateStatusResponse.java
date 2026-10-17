package com.pw01.webserver.guild.dto;

import java.util.List;

/**
 * G4 create: 지금 길드를 만들 수 있나. allowed = 미소속이고 requirements가 모두 충족.
 * 서버 길드 수 상한은 넣지 않는다(만들 때 판정 — 화면을 연 뒤 바뀔 수 있음).
 */
public record GuildCreateStatusResponse(boolean allowed, List<GuildRequirementResponse> requirements) {
}
