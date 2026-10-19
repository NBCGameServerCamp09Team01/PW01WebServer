package com.pw01.webserver.stageplay.controller;

import com.pw01.webserver.auth.interceptor.LoginAccount;
import com.pw01.webserver.stageplay.dto.PlayCheckpointResponse;
import com.pw01.webserver.stageplay.dto.PlayCheckpointSaveRequest;
import com.pw01.webserver.stageplay.dto.PlayCheckpointSaveResponse;
import com.pw01.webserver.stageplay.dto.StagePlayResponse;
import com.pw01.webserver.stageplay.dto.StagePlayStartRequest;
import com.pw01.webserver.stageplay.service.PlayCheckpointService;
import com.pw01.webserver.stageplay.service.StagePlayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 내 스테이지 플레이 API(공유 초안 stage-play-api-draft-1009.md P1~P4, 루트 docs/contracts/stage-play-api.md C1·C2). 인증 필요(WebConfig "/**").
 * me는 토큰의 계정이다. 응답에 accountId가 있어 누구의 플레이인지 보인다.
 */
@RestController
@RequestMapping("/accounts/me/stage-plays")
@RequiredArgsConstructor
public class StagePlayController {

    private final StagePlayService stagePlayService;
    private final PlayCheckpointService playCheckpointService;

    /** P1 시작. 201 + Location(같은 requestId 재전송도 같은 내용으로 201) */
    @PostMapping
    public ResponseEntity<StagePlayResponse> start(@LoginAccount Long accountId,
                                                   @Valid @RequestBody StagePlayStartRequest request) {
        StagePlayResponse play = stagePlayService.start(accountId, request);
        return ResponseEntity.created(URI.create("/accounts/me/stage-plays/" + play.stagePlayId())).body(play);
    }

    /** P2 내 진행 중 플레이. 없으면 204(본문 없음) */
    @GetMapping("/current")
    public ResponseEntity<StagePlayResponse> current(@LoginAccount Long accountId) {
        return stagePlayService.getCurrent(accountId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** P3 내 플레이 하나. 남의 플레이도 404 */
    @GetMapping("/{stagePlayId}")
    public StagePlayResponse get(@LoginAccount Long accountId, @PathVariable String stagePlayId) {
        return stagePlayService.get(accountId, stagePlayId);
    }

    /** P4 포기(실패로 끝냄, 결과·보상 없음) */
    @PostMapping("/{stagePlayId}/abandon")
    public StagePlayResponse abandon(@LoginAccount Long accountId, @PathVariable String stagePlayId) {
        return stagePlayService.abandon(accountId, stagePlayId);
    }

    /** C1 체크포인트 저장(덮어씀). 웨이브를 클리어할 때마다 게임이 올린다 */
    @PutMapping("/{stagePlayId}/checkpoint")
    public PlayCheckpointSaveResponse saveCheckpoint(@LoginAccount Long accountId, @PathVariable String stagePlayId,
                                                     @Valid @RequestBody PlayCheckpointSaveRequest request) {
        return playCheckpointService.save(accountId, stagePlayId, request);
    }

    /** C2 체크포인트 받기. 없으면 204(본문 없음) */
    @GetMapping("/{stagePlayId}/checkpoint")
    public ResponseEntity<PlayCheckpointResponse> getCheckpoint(@LoginAccount Long accountId,
                                                                @PathVariable String stagePlayId) {
        return playCheckpointService.get(accountId, stagePlayId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

}
