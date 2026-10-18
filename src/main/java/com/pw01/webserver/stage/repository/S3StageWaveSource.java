package com.pw01.webserver.stage.repository;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3 객체 하나(pw01.stage-content.s3.bucket·key)를 웨이브 정의 파일로 읽는다(prod).
 * 자격 증명은 SDK 기본 찾기 순서의 EC2 인스턴스 역할(컨테이너는 메타데이터 홉 2가 필요), 지역은 설정 값.
 * 서버 역할에는 그 경로의 s3:GetObject만 있다(s3:ListBucket 없음). 그래서 키가 없어도 404가 아니라 403이 온다.
 * 이 빈이 닫힐 때 S3 클라이언트도 닫는다(@Bean의 close 추론).
 */
public class S3StageWaveSource implements StageWaveSource, AutoCloseable {

    private final S3Client s3;
    private final String bucket;
    private final String key;

    public S3StageWaveSource(S3Client s3, String bucket, String key) {
        this.s3 = s3;
        this.bucket = bucket;
        this.key = key;
    }

    @Override
    public StageWaveFile read() {
        try {
            ResponseBytes<GetObjectResponse> object = s3.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            return new StageWaveFile(object.asByteArray(), object.response().eTag());
        } catch (S3Exception e) {
            throw new StageWaveSourceException(describe() + "을 읽지 못했습니다 status=" + e.statusCode()
                    + hint(e.statusCode()), e);
        } catch (SdkClientException e) {
            throw new StageWaveSourceException(describe() + "에 닿지 못했습니다(자격 증명·지역·네트워크): "
                    + e.getMessage(), e);
        }
    }

    /** 상태 코드로 짐작되는 원인. 로그 한 줄로 찾게 한다 */
    private static String hint(int status) {
        return switch (status) {
            case 403 -> " (서버 역할에 이 경로 읽기 권한이 없거나 키가 없음. 목록 권한이 없어 없는 키도 403)";
            case 404 -> " (버킷 또는 키가 없음)";
            case 301 -> " (버킷 지역이 pw01.stage-content.s3.region과 다름)";
            default -> "";
        };
    }

    @Override
    public String describe() {
        return "s3://" + bucket + "/" + key;
    }

    @Override
    public void close() {
        s3.close();
    }

}
