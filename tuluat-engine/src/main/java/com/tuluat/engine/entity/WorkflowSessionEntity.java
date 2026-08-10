package com.tuluat.engine.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "workflow_sessions")
public class WorkflowSessionEntity {

	@Id
	@Column(name = "session_id")
	private UUID sessionId;

	@Column(name = "workflow_name", nullable = false)
	private String workflowName;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "current_node_id")
	private String currentNodeId;

	@Column(name = "loop_count", nullable = false)
	private int loopCount = 0;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "context_data", columnDefinition = "jsonb")
	private String contextData = "{}";

	@Column(name = "created_at")
	private OffsetDateTime createdAt = OffsetDateTime.now();

	@Column(name = "updated_at")
	private OffsetDateTime updatedAt = OffsetDateTime.now();

	public UUID getSessionId() {
		return sessionId;
	}
	public void setSessionId(UUID sessionId) {
		this.sessionId = sessionId;
	}
	public String getWorkflowName() {
		return workflowName;
	}
	public void setWorkflowName(String workflowName) {
		this.workflowName = workflowName;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public String getCurrentNodeId() {
		return currentNodeId;
	}
	public void setCurrentNodeId(String currentNodeId) {
		this.currentNodeId = currentNodeId;
	}
	public int getLoopCount() {
		return loopCount;
	}
	public void setLoopCount(int loopCount) {
		this.loopCount = loopCount;
	}
	public String getContextData() {
		return contextData;
	}
	public void setContextData(String contextData) {
		this.contextData = contextData;
	}
	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}
	public void setCreatedAt(OffsetDateTime createdAt) {
		this.createdAt = createdAt;
	}
	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
	public void setUpdatedAt(OffsetDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
}
