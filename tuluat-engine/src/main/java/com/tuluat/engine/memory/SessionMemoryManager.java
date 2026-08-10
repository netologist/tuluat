package com.tuluat.engine.memory;

import com.tuluat.engine.entity.SessionShortMemoryEntity;
import com.tuluat.engine.repository.SessionShortMemoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SessionMemoryManager {

	private final SessionShortMemoryRepository shortMemoryRepository;

	public SessionMemoryManager(SessionShortMemoryRepository shortMemoryRepository) {
		this.shortMemoryRepository = shortMemoryRepository;
	}

	@Transactional
	public SessionShortMemoryEntity saveShortMemory(UUID sessionId, String agentName, String role, String content) {
		SessionShortMemoryEntity entity = new SessionShortMemoryEntity();
		entity.setSessionId(sessionId);
		entity.setAgentName(agentName);
		entity.setRole(role);
		entity.setContent(content);
		return shortMemoryRepository.save(entity);
	}

	@Transactional(readOnly = true)
	public List<SessionShortMemoryEntity> getShortMemory(UUID sessionId) {
		return shortMemoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
	}

	@Transactional
	public void clearShortMemory(UUID sessionId) {
		shortMemoryRepository.deleteBySessionId(sessionId);
	}
}
