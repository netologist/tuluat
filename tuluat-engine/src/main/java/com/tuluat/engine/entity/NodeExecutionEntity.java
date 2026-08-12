package com.tuluat.engine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "workflow_session_node_executions", indexes = {
		@Index(name = "idx_node_sessions_session_id", columnList = "session_id"),
		@Index(name = "idx_node_sessions_node_id", columnList = "node_id")})
public class NodeExecutionEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "session_id", nullable = false)
	private UUID sessionId;

	@Column(name = "node_id", nullable = false)
	private String nodeId;

	@Column(name = "agent_name")
	private String agentName;

	@Column(name = "provider")
	private String provider;

	@Column(name = "model")
	private String model;

	@Column(name = "input_prompt", columnDefinition = "TEXT")
	private String inputPrompt;

	@Column(name = "output_text", columnDefinition = "TEXT")
	private String outputText;

	@Column(name = "start_time")
	private OffsetDateTime startTime;

	@Column(name = "end_time")
	private OffsetDateTime endTime;

	@Column(name = "duration_ms")
	private long durationMs;

	@Column(name = "total_tokens")
	private long totalTokens;

	@Column(name = "input_tokens")
	private long inputTokens;

	@Column(name = "output_tokens")
	private long outputTokens;

	@Column(name = "cost_usd", precision = 20, scale = 6)
	private BigDecimal costUsd = BigDecimal.ZERO;

	@Column(name = "status", nullable = false)
	private String status = "COMPLETED";

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;
}
