package com.pw01.webserver.example.dto;

import com.pw01.webserver.example.entity.Example;

import java.time.Instant;

/**
 * 학습용 예시, 실제 기능 아님. 응답 DTO는 record로 두고, 엔티티 → DTO 변환은 정적 메서드 from(entity) 한 곳에 모은다.
 * 엔티티는 API 밖으로 내보내지 않는다.
 */
public record ExampleResponse(Long id, String name, String description, Instant createdAt) {

    public static ExampleResponse from(Example example) {
        return new ExampleResponse(example.getId(), example.getName(), example.getDescription(), example.getCreatedAt());
    }

}
