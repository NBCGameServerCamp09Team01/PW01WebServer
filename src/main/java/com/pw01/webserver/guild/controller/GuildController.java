package com.pw01.webserver.guild.controller;

import com.pw01.webserver.guild.dto.GuildDetailResponse;
import com.pw01.webserver.guild.dto.GuildListResponse;
import com.pw01.webserver.guild.service.GuildQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 길드 정보(guild-api.md G1·G2, 만들기 G3는 W5). 공개 경로가 아니라 인증 인터셉터를 거친다(WebConfig "/**").
 * 쿼리 값 판정(name·cursor·size)은 GuildQueryService가 한다. size 자리에 숫자가 아닌 값이 오면 공통 처리(VALIDATION_FAILED).
 */
@RestController
@RequestMapping("/guilds")
@RequiredArgsConstructor
public class GuildController {

    private final GuildQueryService guildQueryService;

    /** G1 길드 목록(최근 생성 순, 이름 앞부분 검색, 커서) */
    @GetMapping
    public GuildListResponse list(@RequestParam(required = false) String name,
                                  @RequestParam(required = false) String cursor,
                                  @RequestParam(required = false) Integer size) {
        return guildQueryService.list(name, cursor, size);
    }

    /** G2 길드 하나 */
    @GetMapping("/{guildId}")
    public GuildDetailResponse get(@PathVariable String guildId) {
        return guildQueryService.get(guildId);
    }

}
