package com.tuluat.engine.rag.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Filesystem-backed object storage for development and CI (ADR 008). Active
 * when {@code tuluat.rag.storage.type=local} (default).
 */
@Component
@ConditionalOnProperty(name = "tuluat.rag.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorage implements ObjectStorage {

	private static final Logger log = LoggerFactory.getLogger(LocalObjectStorage.class);

	private final Path root;

	public LocalObjectStorage(@Value("${tuluat.rag.storage.local-dir:./data/rag}") String localDir) {
		this.root = Paths.get(localDir).toAbsolutePath().normalize();
		try {
			Files.createDirectories(root);
		} catch (IOException e) {
			throw new IllegalStateException("Cannot create RAG local storage dir: " + root, e);
		}
		log.info("LocalObjectStorage root: {}", root);
	}

	@Override
	public void put(String key, byte[] content, String contentType) {
		Path target = resolve(key);
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, content);
		} catch (IOException e) {
			throw new StorageException("Failed to write object: " + key, e);
		}
	}

	@Override
	public Optional<StoredObject> get(String key) {
		Path target = resolve(key);
		if (!Files.exists(target)) {
			return Optional.empty();
		}
		try {
			return Optional.of(new StoredObject(key, Files.readAllBytes(target), probeContentType(target)));
		} catch (IOException e) {
			throw new StorageException("Failed to read object: " + key, e);
		}
	}

	@Override
	public void delete(String key) {
		Path target = resolve(key);
		try {
			Files.deleteIfExists(target);
		} catch (IOException e) {
			throw new StorageException("Failed to delete object: " + key, e);
		}
	}

	@Override
	public List<String> list(String prefix) {
		Path start = resolve(prefix);
		if (!Files.isDirectory(start)) {
			return List.of();
		}
		try (Stream<Path> paths = Files.walk(start)) {
			return paths.filter(Files::isRegularFile).map(p -> root.relativize(p).toString().replace('\\', '/'))
					.sorted().toList();
		} catch (IOException e) {
			throw new StorageException("Failed to list objects under: " + prefix, e);
		}
	}

	/** Prevents path traversal outside the storage root. */
	private Path resolve(String key) {
		Path p = root.resolve(key).normalize();
		if (!p.startsWith(root)) {
			throw new StorageException("Invalid object key (path traversal): " + key);
		}
		return p;
	}

	private String probeContentType(Path target) {
		try {
			String t = Files.probeContentType(target);
			return t != null ? t : "application/octet-stream";
		} catch (IOException e) {
			return "application/octet-stream";
		}
	}

	/** Thrown on storage I/O or key violations. */
	public static class StorageException extends RuntimeException {
		public StorageException(String message, Throwable cause) {
			super(message, cause);
		}

		public StorageException(String message) {
			super(message);
		}
	}
}
