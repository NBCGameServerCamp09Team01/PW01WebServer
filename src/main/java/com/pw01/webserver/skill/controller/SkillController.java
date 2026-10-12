package com.pw01.webserver.skill.controller;

import com.pw01.webserver.auth.interceptor.LoginAccount;
import com.pw01.webserver.skill.dto.SkillListResponse;
import com.pw01.webserver.skill.dto.SkillUnlockRequest;
import com.pw01.webserver.skill.dto.SkillUnlockResponse;
import com.pw01.webserver.skill.service.SkillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 스킬 해금 API(루트 docs/contracts/skill-api.md). 인증 필요(WebConfig "/**") */
@RestController
@RequestMapping("/accounts/me")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    /** K1 스킬 하나 해금. 같은 requestId 재전송은 처음 응답 */
    @PostMapping("/skill-unlocks")
    public SkillUnlockResponse unlock(@LoginAccount Long accountId, @Valid @RequestBody SkillUnlockRequest request) {
        return skillService.unlock(accountId, request);
    }

    /** K2 스킬마다 상태와 조건 진행도(서버 판정) */
    @GetMapping("/skills")
    public SkillListResponse list(@LoginAccount Long accountId) {
        return skillService.list(accountId);
    }

}
