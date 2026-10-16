package com.pw01.webserver.guild.controller;

import com.pw01.webserver.auth.interceptor.LoginAccount;
import com.pw01.webserver.guild.dto.GuildCreateRequest;
import com.pw01.webserver.guild.dto.GuildDetailResponse;
import com.pw01.webserver.guild.dto.GuildListResponse;
import com.pw01.webserver.guild.dto.MyGuildResponse;
import com.pw01.webserver.guild.service.GuildCommandService;
import com.pw01.webserver.guild.service.GuildQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 공개 길드 정보와 만들기(guild-api.md G1·G2·G3). 공개 경로가 아니라 인증 인터셉터를 거친다(WebConfig "/**").
 * 쿼리 값 판정(name·cursor·size)은 GuildQueryService가 한다. size 자리에 숫자가 아닌 값이 오면 공통 처리(VALIDATION_FAILED).
 */
@RestController
@RequestMapping("/guilds")
@RequiredArgsConstructor
public class GuildController {

    private final GuildQueryService guildQueryService;
    private final GuildCommandService guildCommandService;

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

    /** G3 길드 만들기. 201 + 내 길드(G4 모양), Location은 내 길드 경로. 같은 requestId 재전송은 처음 응답 */
    @PostMapping
    public ResponseEntity<MyGuildResponse> create(@LoginAccount Long accountId,
                                                  @Valid @RequestBody GuildCreateRequest request) {
        MyGuildResponse created = guildCommandService.create(accountId, request);
        return ResponseEntity.created(URI.create("/accounts/me/guild")).body(created);
    }

}
