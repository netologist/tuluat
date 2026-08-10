package com.tuluat.engine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Setter
@Getter
@Entity
@Table(name = "workflow_sessions", indexes = {@Index(name = "idx_workflow_name", columnList = "workflow_name"),
		@Index(name = "idx_status", columnList = "status")})
public class WorkflowSessionEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "session_id")
	private UUID sessionId;

	@Column(name = "workflow_name", nullable = false)
	private String workflowName;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "current_node_id")
	private String currentNodeId;

	@Column(name = "loop_count", nullable = false)
	private int loopCount;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "context_data", columnDefinition = "jsonb")
	private String contextData = "{}";

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private OffsetDateTime updatedAt;

	@Version
	private Long version;

}
