package com.tuluat.engine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Setter
@Getter
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

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt = OffsetDateTime.now();

	@Column(name = "updated_at")
	private OffsetDateTime updatedAt = OffsetDateTime.now();

}
