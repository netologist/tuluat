package com.tuluat.ai.repository;

import com.tuluat.ai.entity.WorkflowSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowSessionRepository extends JpaRepository<WorkflowSessionEntity, UUID> {
    List<WorkflowSessionEntity> findByWorkflowName(String workflowName);
    List<WorkflowSessionEntity> findByStatus(String status);
}
