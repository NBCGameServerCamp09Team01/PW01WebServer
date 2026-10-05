package com.pw01.webserver.example.repository;

import com.pw01.webserver.example.entity.Example;
import org.springframework.data.jpa.repository.JpaRepository;

/** 학습용 예시, 실제 기능 아님. 메서드 이름으로 쿼리를 만든다(existsByName → SELECT … WHERE name = ?) */
public interface ExampleRepository extends JpaRepository<Example, Long> {

    boolean existsByName(String name);

}
