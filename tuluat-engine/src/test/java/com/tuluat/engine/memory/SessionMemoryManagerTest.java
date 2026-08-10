package com.tuluat.engine.memory;

import com.tuluat.engine.entity.SessionShortMemoryEntity;
import com.tuluat.engine.repository.SessionShortMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SessionMemoryManagerTest {

	private SessionShortMemoryRepository shortMemoryRepository;
	private SessionMemoryManager memoryManager;

	@BeforeEach
	void setUp() {
		shortMemoryRepository = mock(SessionShortMemoryRepository.class);
		memoryManager = new SessionMemoryManager(shortMemoryRepository);

		when(shortMemoryRepository.save(any(SessionShortMemoryEntity.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	@DisplayName("Should save short memory message")
	void testSaveShortMemory() {
		UUID sessionId = UUID.randomUUID();
		SessionShortMemoryEntity result = memoryManager.saveShortMemory(sessionId, "researcher-agent", "assistant",
				"Research result");

		assertNotNull(result);
		assertEquals(sessionId, result.getSessionId());
		assertEquals("researcher-agent", result.getAgentName());
		assertEquals("assistant", result.getRole());
		assertEquals("Research result", result.getContent());

		verify(shortMemoryRepository, times(1)).save(any(SessionShortMemoryEntity.class));
	}

	@Test
	@DisplayName("Should fetch short memory messages for session")
	void testGetShortMemory() {
		UUID sessionId = UUID.randomUUID();
		SessionShortMemoryEntity msg1 = new SessionShortMemoryEntity();
		msg1.setSessionId(sessionId);
		msg1.setContent("Message 1");

		when(shortMemoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of(msg1));

		List<SessionShortMemoryEntity> memory = memoryManager.getShortMemory(sessionId);

		assertNotNull(memory);
		assertEquals(1, memory.size());
		assertEquals("Message 1", memory.get(0).getContent());
	}
}
