package com.pw01.webserver.guild.controller;

import com.pw01.webserver.auth.interceptor.LoginAccount;
import com.pw01.webserver.guild.dto.MyGuildResponse;
import com.pw01.webserver.guild.service.GuildQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 길드(guild-api.md G4, 신청·수락·탈퇴·해체 G5~G12는 W5·W6). 로그인한 계정 기준이라 경로에 계정 ID가 없다.
 */
@RestController
@RequestMapping("/accounts/me/guild")
@RequiredArgsConstructor
public class MyGuildController {

    private final GuildQueryService guildQueryService;

    /** G4 내 길드 화면 전체. 미소속도 200 */
    @GetMapping
    public MyGuildResponse myGuild(@LoginAccount Long accountId) {
        return guildQueryService.myGuild(accountId);
    }

}
