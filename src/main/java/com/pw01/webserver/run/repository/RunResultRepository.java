package com.pw01.webserver.run.repository;

import com.pw01.webserver.run.entity.RunResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunResultRepository extends JpaRepository<RunResult, String> {
}
