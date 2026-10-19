package com.pw01.webserver.guild.dto;

import java.time.Instant;
import java.util.List;

/**
 * G4 내 길드 화면 전체(guild-api.md G4). 늘 200이고, 미소속이면 guild = null·members = [].
 *
 * @param guild                   소속 길드 + 내 역할. 미소속이면 null
 * @param members                 길드원(길드장 먼저, 그다음 들어온 순). 미소속이면 빈 목록
 * @param pendingJoinRequestCount 받은 대기 중 신청 수. 길드장이 아니면 0
 * @param myJoinRequests          내 대기 중 신청(유효 기간 안, 오래된 순). 길드원이면 빈 목록
 * @param create                  길드 만들기 가능 여부와 조건
 * @param rejoinAvailableAt       재가입 대기가 끝나는 시각. Lv2 전에는 늘 null
 */
public record MyGuildResponse(MyGuildInfoResponse guild, List<GuildMemberResponse> members,
                              int pendingJoinRequestCount, List<GuildJoinRequestResponse> myJoinRequests,
                              GuildCreateStatusResponse create, Instant rejoinAvailableAt) {
}
