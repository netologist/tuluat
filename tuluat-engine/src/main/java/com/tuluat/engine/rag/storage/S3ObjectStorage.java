package com.tuluat.engine.rag.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * S3-compatible object storage via MinIO SDK (ADR 008). Active when
 * {@code tuluat.rag.storage.type=s3}. Credentials come from environment
 * variables; no secrets in manifests.
 */
@Component
@ConditionalOnProperty(name = "tuluat.rag.storage.type", havingValue = "s3")
@Slf4j
public class S3ObjectStorage implements ObjectStorage {
	private final MinioClient client;
	private final String bucket;

	public S3ObjectStorage(@Value("${tuluat.rag.storage.s3.endpoint}") String endpoint,
			@Value("${tuluat.rag.storage.s3.bucket:rag-documents}") String bucket,
			@Value("${tuluat.rag.storage.s3.access-key:}") String accessKey,
			@Value("${tuluat.rag.storage.s3.secret-key:}") String secretKey) {
		this.bucket = bucket;
		this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
		try {
			boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
			if (!exists) {
				client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
				log.info("Created S3 bucket: {}", bucket);
			}
		} catch (Exception e) {
			log.warn("S3 bucket [{}] check failed: {}", bucket, e.getMessage());
		}
		log.info("S3ObjectStorage endpoint={} bucket={}", endpoint, bucket);
	}

	@Override
	public void put(String key, byte[] content, String contentType) {
		try (ByteArrayInputStream in = new ByteArrayInputStream(content)) {
			client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(in, content.length, -1)
					.contentType(contentType == null ? "application/octet-stream" : contentType).build());
		} catch (Exception e) {
			throw new LocalObjectStorage.StorageException("S3 put failed: " + key, e);
		}
	}

	@Override
	public Optional<StoredObject> get(String key) {
		try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
			byte[] bytes = in.readAllBytes();
			return Optional.of(new StoredObject(key, bytes, "application/octet-stream"));
		} catch (Exception e) {
			// MinIO raises ErrorResponseException for missing keys
			return Optional.empty();
		}
	}

	@Override
	public void delete(String key) {
		try {
			client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
		} catch (Exception e) {
			throw new LocalObjectStorage.StorageException("S3 delete failed: " + key, e);
		}
	}

	@Override
	public List<String> list(String prefix) {
		List<String> keys = new ArrayList<>();
		try {
			Iterable<Result<Item>> results = client
					.listObjects(ListObjectsArgs.builder().bucket(bucket).prefix(prefix).build());
			for (Result<Item> r : results) {
				keys.add(r.get().objectName());
			}
		} catch (Exception e) {
			throw new LocalObjectStorage.StorageException("S3 list failed: " + prefix, e);
		}
		return keys;
	}
}
