package com.pw01.webserver.example.controller;

import com.pw01.webserver.example.dto.ExampleCreateRequest;
import com.pw01.webserver.example.dto.ExampleResponse;
import com.pw01.webserver.example.service.ExampleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 학습용 예시, 실제 기능 아님. 명세: 루트 docs/contracts/example-api.md
 * Controller는 경로와 요청 검증(@Valid)만 맡고 Service를 부른다. Repository를 직접 부르지 않는다.
 */
@RestController
@RequestMapping("/api/v1/examples")
@RequiredArgsConstructor
public class ExampleController {

    private final ExampleService exampleService;

    @PostMapping
    public ResponseEntity<ExampleResponse> create(@Valid @RequestBody ExampleCreateRequest request) {
        ExampleResponse created = exampleService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/examples/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public ExampleResponse get(@PathVariable Long id) {
        return exampleService.get(id);
    }

}
