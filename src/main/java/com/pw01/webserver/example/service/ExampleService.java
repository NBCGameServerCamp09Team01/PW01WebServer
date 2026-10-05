package com.pw01.webserver.example.service;

import com.pw01.webserver.common.error.ConflictException;
import com.pw01.webserver.common.error.NotFoundException;
import com.pw01.webserver.example.dto.ExampleCreateRequest;
import com.pw01.webserver.example.dto.ExampleResponse;
import com.pw01.webserver.example.entity.Example;
import com.pw01.webserver.example.repository.ExampleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학습용 예시, 실제 기능 아님. 판정과 트랜잭션은 Service가 맡고, 거절은 상태별 예외에 오류 코드를 담아 던진다.
 * 오류 코드는 루트 docs/contracts/example-api.md "오류" 표에 먼저 적은 것을 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExampleService {

    static final String NOT_FOUND = "EXAMPLE_NOT_FOUND";
    static final String NAME_DUPLICATED = "EXAMPLE_NAME_DUPLICATED";

    private final ExampleRepository exampleRepository;

    @Transactional
    public ExampleResponse create(ExampleCreateRequest request) {
        if (exampleRepository.existsByName(request.name())) {
            throw nameDuplicated();
        }
        try {
            // saveAndFlush: INSERT를 여기서 실행해 유일 제약 위반을 이 자리에서 잡는다
            Example saved = exampleRepository.saveAndFlush(new Example(request.name(), request.description()));
            return ExampleResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            // 같은 이름 요청 두 개가 동시에 오면 위 확인을 함께 통과할 수 있다. 마지막 방어는 DB 유일 제약(uk_example_name)
            throw nameDuplicated();
        }
    }

    public ExampleResponse get(Long id) {
        return exampleRepository.findById(id)
                .map(ExampleResponse::from)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND, "예시를 찾을 수 없습니다."));
    }

    private static ConflictException nameDuplicated() {
        return new ConflictException(NAME_DUPLICATED, "같은 이름의 예시가 이미 있습니다.");
    }

}
