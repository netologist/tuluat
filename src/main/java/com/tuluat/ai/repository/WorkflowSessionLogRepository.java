package com.tuluat.ai.repository;

import com.tuluat.ai.entity.WorkflowSessionLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowSessionLogRepository extends JpaRepository<WorkflowSessionLogEntity, Long> {
    List<WorkflowSessionLogEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
