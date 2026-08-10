package com.tuluat.engine.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "workflow_session_logs")
public class WorkflowSessionLogEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "session_id", nullable = false)
	private UUID sessionId;

	@Column(name = "node_id")
	private String nodeId;

	@Column(name = "log_level", nullable = false)
	private String logLevel = "INFO";

	@Column(name = "message", columnDefinition = "TEXT", nullable = false)
	private String message;

	@Column(name = "created_at")
	private OffsetDateTime createdAt = OffsetDateTime.now();

	public Long getId() {
		return id;
	}
	public UUID getSessionId() {
		return sessionId;
	}
	public void setSessionId(UUID sessionId) {
		this.sessionId = sessionId;
	}
	public String getNodeId() {
		return nodeId;
	}
	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}
	public String getLogLevel() {
		return logLevel;
	}
	public void setLogLevel(String logLevel) {
		this.logLevel = logLevel;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}
}
