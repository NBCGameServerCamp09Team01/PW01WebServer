package com.pw01.webserver.guild.controller;

import com.pw01.webserver.auth.interceptor.LoginAccount;
import com.pw01.webserver.guild.dto.GuildJoinRequestCreateRequest;
import com.pw01.webserver.guild.dto.GuildJoinRequestResponse;
import com.pw01.webserver.guild.service.GuildCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 내가 보낸 가입 신청(guild-api.md G5·G6). 받은 신청(길드장)은 /accounts/me/guild/join-requests(W6).
 */
@RestController
@RequestMapping("/accounts/me/guild-join-requests")
@RequiredArgsConstructor
public class MyGuildJoinRequestController {

    private final GuildCommandService guildCommandService;

    /** G5 가입 신청. 201 + 신청 하나 */
    @PostMapping
    public ResponseEntity<GuildJoinRequestResponse> apply(@LoginAccount Long accountId,
                                                          @Valid @RequestBody GuildJoinRequestCreateRequest request) {
        GuildJoinRequestResponse created = guildCommandService.apply(accountId, request);
        return ResponseEntity.created(URI.create("/accounts/me/guild-join-requests/" + created.joinRequestId()))
                .body(created);
    }

    /** G6 신청 취소. 없거나 남의 신청이어도 204(다시 보내도 결과가 같음) */
    @DeleteMapping("/{joinRequestId}")
    public ResponseEntity<Void> cancel(@LoginAccount Long accountId, @PathVariable String joinRequestId) {
        guildCommandService.cancel(accountId, joinRequestId);
        return ResponseEntity.noContent().build();
    }

}
