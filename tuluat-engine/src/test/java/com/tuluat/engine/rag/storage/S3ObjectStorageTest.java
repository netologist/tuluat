package com.tuluat.engine.rag.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class S3ObjectStorageTest {

	@Test
	void constructorHandlesUnreachableEndpointGracefully() {
		// Constructor catches bucketExists exception and logs warning
		assertDoesNotThrow(
				() -> new S3ObjectStorage("http://127.0.0.1:59999", "test-bucket", "minioadmin", "minioadmin"));
	}
}
