-- 학습용 예시 테이블(실제 기능 아님, docs/guides/example-api.md).
-- 예시 API를 지울 때 이 파일은 고치지 않고, 지우는 마이그레이션(V{다음 번호}__drop_example.sql)을 더한다.
CREATE TABLE example
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(200) NULL,
    created_at  DATETIME(3)  NOT NULL,
    updated_at  DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_example_name UNIQUE (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
