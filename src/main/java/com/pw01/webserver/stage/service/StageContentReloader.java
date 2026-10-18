package com.pw01.webserver.stage.service;

import com.pw01.webserver.common.master.MasterDataException;
import com.pw01.webserver.common.master.MasterDataLoader;
import com.pw01.webserver.stage.master.StageWaveMasterFile;
import com.pw01.webserver.stage.repository.StageWaveFile;
import com.pw01.webserver.stage.repository.StageWaveSource;
import com.pw01.webserver.stage.repository.StageWaveSourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;

/**
 * 웨이브 정의 다시 읽기(판 12, stage-api.md "웨이브 구성 바꾸기(재시작 없이)"). 서버를 다시 띄우지 않고
 * 출처(prod = S3)의 stage-waves.json이 바뀌었으면 검사한 뒤 StageCatalog의 덩어리를 통째로 바꾼다.
 * <p>
 * 부르는 곳은 셋이다: 관리 포트 신호(signal), 다른 서버의 알림(relay), 안전망 주기 확인(poll). 셋이 겹쳐도
 * 한 번에 하나만 돈다(synchronized). 바꾸는 쪽은 이 클래스 하나라서 StageCatalog.replace는 늘 성공한다.
 * <ol>
 *   <li>HEAD로 ETag만 본다. 지금 정의와 같으면 끝(UNCHANGED). 지난번 거절한 파일과 같으면 다시 읽지 않는다(REJECTED)</li>
 *   <li>내용을 받는다. 바이트가 지금과 같으면(같은 파일을 다시 올림) 끝(UNCHANGED)</li>
 *   <li>모양 검사(모르는 칸 실패) → 내용이 바뀌었는데 version이 그대로면 거절(같은 waveVersion에 다른 구성이 저장되지 않게).
 *       version이 내려가는 것(되돌리기)은 받는다</li>
 *   <li>기동 검사 그대로(이미지 안 stages·monsters·difficulties와 맞춤) → 통과하면 교체(CHANGED)</li>
 * </ol>
 * 어느 단계든 실패하면 지금 정의를 그대로 쓴다. 이미 시작한 판은 시작 때 저장한 구성이라 영향이 없다.
 */
@Service
public class StageContentReloader {

    private static final Logger log = LoggerFactory.getLogger(StageContentReloader.class);

    public enum Result {
        /** 새 정의로 바꿈 */
        CHANGED,
        /** 지금과 같은 파일 */
        UNCHANGED,
        /** 바뀌었지만 검사에 걸려 지금 정의 유지 */
        REJECTED,
        /** 출처를 읽지 못해 지금 정의 유지 */
        SOURCE_ERROR,
        /** 이 서버는 리소스 파일을 씀(로컬·시험). 다시 읽지 않음 */
        NOT_S3
    }

    /**
     * 다시 읽기 한 번의 결과.
     *
     * @param before      다시 읽기 전 waveVersion
     * @param waveVersion 다시 읽은 뒤 waveVersion(바뀌지 않았으면 before와 같음)
     * @param reason      REJECTED·SOURCE_ERROR일 때 이유. 그 밖에는 null
     */
    public record Outcome(Result result, String before, String waveVersion, String reason) {
    }

    /** 거절한 파일 하나 */
    public record Rejection(Instant at, String reason) {
    }

    /**
     * 지금 상태(관리 포트 GET·info).
     *
     * @param lastCheckAt  마지막으로 출처를 확인한 시각. 없으면 null
     * @param lastRejected 마지막으로 거절한 파일. 없으면 null
     */
    public record Status(String source, String waveVersion, Instant loadedAt, Instant lastCheckAt,
                         Rejection lastRejected) {
    }

    private final StageCatalog catalog;
    private final StageWaveSource source;
    private final MasterDataLoader loader;

    // 아래 값은 synchronized 안에서만 바꾸고, status()도 synchronized로 읽는다
    /** 지금 정의를 만든 파일의 ETag·내용 지문·version */
    private String etag;
    private byte[] digest;
    private int version;
    private Instant loadedAt;
    private Instant lastCheckAt;
    private Rejection lastRejected;
    /** 마지막으로 거절한 파일의 ETag. 같은 파일을 주기마다 다시 받지 않으려고 기억한다 */
    private String rejectedEtag;

    public StageContentReloader(StageCatalog catalog, StageWaveSource source, MasterDataLoader loader) {
        this.catalog = catalog;
        this.source = source;
        this.loader = loader;
        StageWaveFile loaded = catalog.loadedWaveFile();
        this.etag = loaded.etag();
        this.digest = sha256(loaded.content());
        this.version = loader.parse(StageCatalog.WAVES_FILE_NAME, loaded.content(), StageWaveMasterFile.class).version();
        this.loadedAt = now();
    }

    /**
     * 출처를 확인하고 바뀌었으면 검사 뒤 바꾼다. 예외를 던지지 않는다(결과로 알림).
     *
     * @param trigger 부른 까닭(signal·relay·poll). 로그에만 쓴다
     */
    public synchronized Outcome reload(String trigger) {
        String before = catalog.waveVersion();
        if (!source.reloadable()) {
            return new Outcome(Result.NOT_S3, before, before, null);
        }
        lastCheckAt = now();
        StageWaveFile file;
        try {
            String headEtag = source.currentEtag();
            if (Objects.equals(headEtag, etag)) {
                return new Outcome(Result.UNCHANGED, before, before, null);
            }
            if (headEtag != null && headEtag.equals(rejectedEtag)) {
                return new Outcome(Result.REJECTED, before, before, lastRejected.reason());
            }
            file = source.read();
        } catch (StageWaveSourceException e) {
            log.warn("[Content] 정의 확인 실패 trigger={} {} — {} 유지", trigger, e.getMessage(), before);
            return new Outcome(Result.SOURCE_ERROR, before, before, e.getMessage());
        }

        byte[] nextDigest = sha256(file.content());
        if (Arrays.equals(nextDigest, digest)) {
            // 같은 내용을 다시 올림(ETag만 다름). 다음부터 HEAD에서 끝나게 ETag만 옮긴다
            etag = file.etag();
            return new Outcome(Result.UNCHANGED, before, before, null);
        }
        try {
            StageWaveMasterFile wavesFile = loader.parse(StageCatalog.WAVES_FILE_NAME, file.content(),
                    StageWaveMasterFile.class);
            if (wavesFile.version() == version) {
                throw new MasterDataException(StageCatalog.WAVES_FILE_NAME, "내용이 바뀌었는데 version(" + version
                        + ")이 그대로입니다. 쓴 적 없는 새 숫자로 올려 주세요.");
            }
            StageContent next = catalog.build(wavesFile);
            catalog.replace(catalog.content(), next);
            etag = file.etag();
            digest = nextDigest;
            version = wavesFile.version();
            loadedAt = now();
            rejectedEtag = null;
            log.info("[Content] 정의 교체 {} → {} reason={}", before, next.waveVersion(), trigger);
            return new Outcome(Result.CHANGED, before, next.waveVersion(), null);
        } catch (MasterDataException e) {
            rejectedEtag = file.etag();
            lastRejected = new Rejection(now(), e.getMessage());
            log.warn("[Content] 정의 교체 거절 reason={} trigger={} — {} 유지", e.getMessage(), trigger, before);
            return new Outcome(Result.REJECTED, before, before, e.getMessage());
        }
    }

    public synchronized Status status() {
        return new Status(source.describe(), catalog.waveVersion(), loadedAt, lastCheckAt, lastRejected);
    }

    /** 이 서버가 다시 읽기를 하는가(S3). 안전망 주기 확인을 켤지 정한다 */
    public boolean reloadable() {
        return source.reloadable();
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
        }
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

}
