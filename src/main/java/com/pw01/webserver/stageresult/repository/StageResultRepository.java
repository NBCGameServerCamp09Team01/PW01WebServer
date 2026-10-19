package com.pw01.webserver.stageresult.repository;

import com.pw01.webserver.stageresult.entity.StageResult;
import org.springframework.data.jpa.repository.JpaRepository;

/** 기본 키 = 스테이지 플레이 ID */
public interface StageResultRepository extends JpaRepository<StageResult, String> {
}
