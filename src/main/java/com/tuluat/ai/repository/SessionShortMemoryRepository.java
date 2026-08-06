package com.tuluat.ai.repository;

import com.tuluat.ai.entity.SessionShortMemoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SessionShortMemoryRepository extends JpaRepository<SessionShortMemoryEntity, Long> {
    List<SessionShortMemoryEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
    void deleteBySessionId(UUID sessionId);
}
