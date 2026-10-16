package com.pw01.webserver.guild.dto;

import java.util.List;

/**
 * G1 길드 목록(최근 생성 순). nextCursor는 hasNext가 false면 null(필드는 늘 있음).
 */
public record GuildListResponse(List<GuildSummaryResponse> guilds, boolean hasNext, String nextCursor) {
}
