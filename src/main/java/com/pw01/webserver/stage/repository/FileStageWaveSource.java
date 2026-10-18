package com.pw01.webserver.stage.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 디스크의 파일 하나를 웨이브 정의 파일로 읽는다(로컬 시험 전용, pw01.stage-content.source=file).
 * AWS 없이 "재시작 없이 바꾸기"를 PC에서 해 보려고 둔다: 파일을 고쳐 저장 → 관리 포트 다시 읽기 → 다른 서버에 알림.
 * compose.apps.yaml의 두 앱이 같은 폴더를 보게 하면 서버 두 대의 교체·알림·안전망까지 S3와 같은 길로 돈다.
 * <p>
 * ETag 대신 파일 내용의 SHA-256을 쓴다. 수정 시각은 Windows·Docker 바인드 마운트에서 믿기 어렵고,
 * 내용 지문이면 같은 내용을 다시 저장해도 "바뀌지 않음"이 된다(S3에 같은 파일을 다시 올렸을 때와 같은 결과).
 * 파일이 작으므로(KB) 확인마다 통째로 읽어도 비용이 없다. 배포(prod)에서는 쓰지 않는다(S3).
 */
public class FileStageWaveSource implements StageWaveSource {

    private final Path path;

    public FileStageWaveSource(Path path) {
        this.path = path;
    }

    @Override
    public StageWaveFile read() {
        byte[] content = readBytes();
        return new StageWaveFile(content, etagOf(content));
    }

    @Override
    public String describe() {
        return "file:" + path.toAbsolutePath().normalize();
    }

    @Override
    public boolean reloadable() {
        return true;
    }

    @Override
    public String currentEtag() {
        return etagOf(readBytes());
    }

    private byte[] readBytes() {
        try {
            return Files.readAllBytes(path);
        } catch (NoSuchFileException e) {
            throw new StageWaveSourceException(describe() + "이 없습니다. 리소스 파일을 복사해 두세요"
                    + "(README \"로컬 로드 밸런서 환경\" 파일 출처)", e);
        } catch (IOException e) {
            throw new StageWaveSourceException(describe() + "을 읽지 못했습니다: " + e.getMessage(), e);
        }
    }

    /** S3 ETag처럼 따옴표로 감싼 내용 지문. 내용이 같으면 같은 값 */
    private static String etagOf(byte[] content) {
        try {
            return "\"sha256-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)) + "\"";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
        }
    }

}
