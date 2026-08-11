package com.tuluat.app.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class WorkflowEventPublisher {

	private final Optional<SimpMessagingTemplate> messagingTemplate;

	public WorkflowEventPublisher(Optional<SimpMessagingTemplate> messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	public void publishSessionState(UUID sessionId, String workflowName, String phase, String currentNode,
			Object output) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("type", "SESSION_STATE_CHANGED");
		payload.put("sessionId", sessionId.toString());
		payload.put("workflowName", workflowName);
		payload.put("phase", phase);
		payload.put("currentNode", currentNode);
		payload.put("output", output);
		payload.put("timestamp", Instant.now().toString());

		sendToTopic("/topic/sessions", payload);
		sendToTopic("/topic/sessions/" + sessionId, payload);
	}

	public void publishApprovalRequest(UUID sessionId, String workflowName, String currentNode, String input) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("type", "APPROVAL_REQUIRED");
		payload.put("sessionId", sessionId.toString());
		payload.put("workflowName", workflowName);
		payload.put("currentNode", currentNode);
		payload.put("input", input);
		payload.put("timestamp", Instant.now().toString());

		sendToTopic("/topic/approvals", payload);
		sendToTopic("/topic/sessions/" + sessionId, payload);
	}

	public void publishApprovalResolved(UUID sessionId, boolean approved, String feedback) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("type", "APPROVAL_RESOLVED");
		payload.put("sessionId", sessionId.toString());
		payload.put("approved", approved);
		payload.put("feedback", feedback);
		payload.put("timestamp", Instant.now().toString());

		sendToTopic("/topic/approvals", payload);
		sendToTopic("/topic/sessions/" + sessionId, payload);
	}

	public void publishLogEntry(UUID sessionId, String node, String level, String message) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("type", "LOG_EMITTED");
		payload.put("sessionId", sessionId.toString());
		payload.put("node", node);
		payload.put("level", level);
		payload.put("message", message);
		payload.put("timestamp", Instant.now().toString());

		sendToTopic("/topic/sessions/" + sessionId + "/logs", payload);
	}

	private void sendToTopic(String destination, Object payload) {
		messagingTemplate.ifPresent(template -> {
			try {
				template.convertAndSend(destination, payload);
			} catch (Exception e) {
				log.warn("Failed to broadcast WebSocket STOMP event to destination {}: {}", destination,
						e.getMessage());
			}
		});
	}
}
