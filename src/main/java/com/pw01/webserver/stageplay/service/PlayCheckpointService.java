package com.pw01.webserver.stageplay.service;

import com.pw01.webserver.config.PlayCheckpointProperties;
import com.pw01.webserver.stageplay.dto.PlayCheckpointResponse;
import com.pw01.webserver.stageplay.dto.PlayCheckpointSaveRequest;
import com.pw01.webserver.stageplay.dto.PlayCheckpointSaveResponse;
import com.pw01.webserver.stageplay.dto.StagePlayIds;
import com.pw01.webserver.stageplay.entity.StagePlay;
import com.pw01.webserver.stageplay.entity.StagePlayStatus;
import com.pw01.webserver.stageplay.repository.PlayCheckpointRedisStore;
import com.pw01.webserver.stageplay.repository.StagePlayRepository;
import com.pw01.webserver.stageplay.repository.StoredPlayCheckpoint;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 플레이 체크포인트 C1 저장·C2 받기(루트 docs/contracts/stage-play-api.md "C1·C2. 체크포인트").
 * 진행 중인 플레이 하나의 마지막 체크포인트를 Redis에 둔다(덮어씀). 판 안 값 state는 게임이 정하는 JSON 객체라
 * 객체인지와 크기만 확인하고 안의 칸은 읽지 않는다(서버 검사는 최소: 내 플레이, 진행 중, 웨이브 순서).
 * 판이 끝나면 PlayCheckpointCleaner가 지운다.
 */
@Service
@RequiredArgsConstructor
public class PlayCheckpointService {

    private static final Logger log = LoggerFactory.getLogger(PlayCheckpointService.class);

    private final StagePlayRepository stagePlayRepository;
    private final PlayCheckpointRedisStore checkpointStore;
    private final PlayCheckpointProperties properties;
    private final JsonMapper jsonMapper;

    /**
     * C1 저장. 처리 순서: 내 플레이(404) → 진행 중·마감(409) → 요청 값(400) → 저장된 것보다 앞선 웨이브(409) → 저장.
     * 마지막 둘은 Redis 스크립트 하나로 한다(늦게 도착한 앞 웨이브가 뒤 웨이브를 덮지 못하게).
     */
    @Transactional(readOnly = true)
    public PlayCheckpointSaveResponse save(Long accountId, String pathStagePlayId, PlayCheckpointSaveRequest request) {
        StagePlay play = findMine(accountId, pathStagePlayId);
        Instant now = now();

        // 결과 제출과 같은 판정: 만료(저장된 EXPIRED 또는 마감만 지남) → EXPIRED, 그 밖에 끝남 → NOT_IN_PROGRESS
        if (play.getStatus() == StagePlayStatus.EXPIRED || (play.isInProgress() && play.isExpiredAt(now))) {
            throw StagePlayErrors.expired();
        }
        if (!play.isInProgress()) {
            throw StagePlayErrors.notInProgress();
        }

        // 마지막 웨이브를 클리어하면 체크포인트 대신 결과를 낸다
        if (request.clearedWave() >= play.getWaveCount()) {
            throw StagePlayErrors.badCheckpointWave(play.getWaveCount());
        }
        JsonNode state = requireStateObject(request.state());

        StoredPlayCheckpoint checkpoint = new StoredPlayCheckpoint(String.valueOf(accountId), request.clearedWave(),
                request.contentVersion(), request.elapsedSeconds(), now, state);
        if (!checkpointStore.put(play.getId(), checkpoint, Duration.between(now, play.getExpiresAt()))) {
            throw StagePlayErrors.checkpointOutOfOrder();
        }

        log.info("플레이 체크포인트 저장 stagePlayId={} accountId={} clearedWave={}/{} contentVersion={}", play.getId(),
                accountId, request.clearedWave(), play.getWaveCount(), request.contentVersion());
        return PlayCheckpointSaveResponse.from(play.getId(), checkpoint);
    }

    /**
     * C2 받기. 없으면 빈 값(204): 한 번도 올리지 않음, 판이 끝나 지움, 마감이 지나 사라짐.
     * 진행 중이 아니거나 마감이 지난 플레이는 Redis를 보지 않고 빈 값이다(지우기에 실패해 남은 값이 나가지 않게).
     */
    @Transactional(readOnly = true)
    public Optional<PlayCheckpointResponse> get(Long accountId, String pathStagePlayId) {
        StagePlay play = findMine(accountId, pathStagePlayId);
        if (!play.isInProgress() || play.isExpiredAt(now())) {
            return Optional.empty();
        }
        return checkpointStore.find(play.getId()).map(checkpoint -> PlayCheckpointResponse.from(play.getId(), checkpoint));
    }

    /** state는 JSON 객체여야 하고, JSON으로 썼을 때 설정 최대 크기 이하여야 한다 */
    private JsonNode requireStateObject(JsonNode state) {
        if (state == null || !state.isObject()) {
            throw StagePlayErrors.badCheckpointState("state는 JSON 객체여야 합니다.");
        }
        long size = jsonMapper.writeValueAsString(state).getBytes(StandardCharsets.UTF_8).length;
        long max = properties.maxStateSize().toBytes();
        if (size > max) {
            throw StagePlayErrors.badCheckpointState("state는 " + max + "바이트 이하여야 합니다(지금 " + size + "바이트).");
        }
        return state;
    }

    private StagePlay findMine(Long accountId, String pathStagePlayId) {
        String id = StagePlayIds.normalize(pathStagePlayId).orElseThrow(StagePlayErrors::badId);
        return stagePlayRepository.findByIdAndAccountId(id, accountId).orElseThrow(StagePlayErrors::notFound);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

}
