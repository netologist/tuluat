package com.tuluat.engine.repository;

import com.tuluat.engine.entity.SessionStatus;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowSessionRepository extends JpaRepository<WorkflowSessionEntity, UUID> {
	List<WorkflowSessionEntity> findByWorkflowName(String workflowName);
	List<WorkflowSessionEntity> findByWorkflowNameOrderByCreatedAtDesc(String workflowName);
	List<WorkflowSessionEntity> findAllByOrderByCreatedAtDesc();
	List<WorkflowSessionEntity> findByStatus(SessionStatus status);
	long countByStatus(SessionStatus status);
}
