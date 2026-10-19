package com.pw01.webserver.guild.controller;

import com.pw01.webserver.auth.interceptor.LoginAccount;
import com.pw01.webserver.guild.dto.GuildActionRequest;
import com.pw01.webserver.guild.dto.GuildApproveResponse;
import com.pw01.webserver.guild.dto.GuildDisbandResponse;
import com.pw01.webserver.guild.dto.GuildJoinRequestListResponse;
import com.pw01.webserver.guild.dto.GuildLeaveResponse;
import com.pw01.webserver.guild.dto.GuildRejectResponse;
import com.pw01.webserver.guild.dto.MyGuildResponse;
import com.pw01.webserver.guild.service.GuildCommandService;
import com.pw01.webserver.guild.service.GuildQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 길드(guild-api.md G4·G7·G8·G9·G11·G12, 추방 G10은 Lv2). 로그인한 계정 기준이라 경로에 계정 ID가 없다.
 * 길드장 기능(G7~G9·G12)의 권한 판정(403 두 종류)은 서비스가 한다.
 */
@RestController
@RequestMapping("/accounts/me/guild")
@RequiredArgsConstructor
public class MyGuildController {

    private final GuildQueryService guildQueryService;
    private final GuildCommandService guildCommandService;

    /** G4 내 길드 화면 전체. 미소속도 200 */
    @GetMapping
    public MyGuildResponse myGuild(@LoginAccount Long accountId) {
        return guildQueryService.myGuild(accountId);
    }

    /** G7 받은 신청(길드장) */
    @GetMapping("/join-requests")
    public GuildJoinRequestListResponse joinRequests(@LoginAccount Long accountId) {
        return guildQueryService.receivedJoinRequests(accountId);
    }

    /** G8 신청 수락(길드장) */
    @PostMapping("/join-requests/{joinRequestId}/approve")
    public GuildApproveResponse approve(@LoginAccount Long accountId, @PathVariable String joinRequestId,
                                        @Valid @RequestBody GuildActionRequest request) {
        return guildCommandService.approve(accountId, joinRequestId, request);
    }

    /** G9 신청 거절(길드장) */
    @PostMapping("/join-requests/{joinRequestId}/reject")
    public GuildRejectResponse reject(@LoginAccount Long accountId, @PathVariable String joinRequestId,
                                      @Valid @RequestBody GuildActionRequest request) {
        return guildCommandService.reject(accountId, joinRequestId, request);
    }

    /** G11 탈퇴(길드장이 혼자면 해체) */
    @PostMapping("/leave")
    public GuildLeaveResponse leave(@LoginAccount Long accountId, @Valid @RequestBody GuildActionRequest request) {
        return guildCommandService.leave(accountId, request);
    }

    /** G12 해체(길드장) */
    @PostMapping("/disband")
    public GuildDisbandResponse disband(@LoginAccount Long accountId, @Valid @RequestBody GuildActionRequest request) {
        return guildCommandService.disband(accountId, request);
    }

}
