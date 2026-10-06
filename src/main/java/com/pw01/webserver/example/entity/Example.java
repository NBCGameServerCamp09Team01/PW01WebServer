package com.pw01.webserver.example.entity;

import com.pw01.webserver.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학습용 예시, 실제 기능 아님(docs/guides/example-api.md). 첫 실제 API(S1)가 병합되면 지운다.
 * 테이블은 db/migration/V1__example.sql이 만든다. 유일 제약 이름은 마이그레이션과 같게 적는다.
 */
@Entity
@Table(name = "example", uniqueConstraints = @UniqueConstraint(name = "uk_example_name", columnNames = "name"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Example extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    public Example(String name, String description) {
        this.name = name;
        this.description = description;
    }

}
