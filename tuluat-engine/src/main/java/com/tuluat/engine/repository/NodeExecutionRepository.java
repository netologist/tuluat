package com.tuluat.engine.repository;

import com.tuluat.engine.entity.NodeExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NodeExecutionRepository extends JpaRepository<NodeExecutionEntity, Long> {
	List<NodeExecutionEntity> findBySessionIdOrderByStartTimeAsc(UUID sessionId);

	void deleteBySessionId(UUID sessionId);
}
