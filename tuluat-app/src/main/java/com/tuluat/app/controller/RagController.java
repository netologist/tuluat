package com.tuluat.app.controller;

import com.tuluat.engine.rag.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for document ingestion into the RAG pipeline (ADR 008).
 * Accepts plain-text documents, chunks them, stores raw content in object
 * storage (MinIO / local), embeds chunks, and persists them for retrieval.
 */
@RestController
@RequestMapping("/api/v1/rag")
@Slf4j
public class RagController {

	private final RagService ragService;

	public RagController(RagService ragService) {
		this.ragService = ragService;
	}

	/**
	 * Ingest a text document into the RAG pipeline.
	 *
	 * <pre>
	 * POST /api/v1/rag/ingest
	 * { "sourceRef": "reports/acme-q4-2025", "content": "Acme Corp Q4 2025..." }
	 * </pre>
	 */
	@PostMapping("/ingest")
	public ResponseEntity<Map<String, Object>> ingest(@RequestBody IngestRequest request) {
		if (request.sourceRef() == null || request.sourceRef().isBlank()) {
			return ResponseEntity.badRequest().body(Map.of("error", "sourceRef is required"));
		}
		if (request.content() == null || request.content().isBlank()) {
			return ResponseEntity.badRequest().body(Map.of("error", "content is required"));
		}
		int chunks = ragService.ingest(request.sourceRef(), request.content());
		log.info("RAG ingested [{}]: {} chunk(s)", request.sourceRef(), chunks);
		return ResponseEntity.ok(Map.of("sourceRef", request.sourceRef(), "chunks", chunks));
	}

	record IngestRequest(String sourceRef, String content) {
	}
}
