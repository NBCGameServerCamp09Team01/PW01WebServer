package com.pw01.webserver.guild.dto;

import java.util.List;

/** G7 받은 신청(유효 기간 안, 오래된 순) */
public record GuildJoinRequestListResponse(List<GuildJoinRequestResponse> joinRequests) {
}
