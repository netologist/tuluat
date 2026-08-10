package com.tuluat.engine.rag.storage;

import java.util.List;
import java.util.Optional;

/**
 * Object storage SPI for RAG source documents (ADR 008). Implementations:
 * {@link LocalObjectStorage} (dev/CI) and {@link S3ObjectStorage} (MinIO,
 * production). Binary-safe.
 */
public interface ObjectStorage {

	/**
	 * Store an object.
	 *
	 * @param key
	 *            storage key, e.g. {@code documents/<source>/<docId>.txt}
	 * @param content
	 *            raw bytes
	 * @param contentType
	 *            MIME type
	 */
	void put(String key, byte[] content, String contentType);

	/**
	 * Retrieve an object by key.
	 */
	Optional<StoredObject> get(String key);

	/**
	 * Delete an object by key.
	 */
	void delete(String key);

	/**
	 * List object keys under a prefix.
	 */
	List<String> list(String prefix);

	/**
	 * Stored object value.
	 */
	record StoredObject(String key, byte[] content, String contentType) {
	}
}
