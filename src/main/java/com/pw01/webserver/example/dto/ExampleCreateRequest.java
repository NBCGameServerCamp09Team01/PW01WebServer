package com.pw01.webserver.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 학습용 예시, 실제 기능 아님. 요청 DTO는 record로 두고 형식 검증(Bean Validation)을 붙인다.
 * 길이 제한은 예시 값이다(루트 docs/contracts/example-api.md).
 */
public record ExampleCreateRequest(

        @NotBlank(message = "이름을 입력해야 합니다.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @Size(max = 200, message = "설명은 200자 이하여야 합니다.")
        String description
) {
}
