package com.tuluat.crd.workflow;

public record MemoryConfig(int shortMemorySize, boolean enableLongMemory, String vectorTableName) {

	public MemoryConfig() {
		this(50, true, "document_vectors");
	}

	public MemoryConfig {
		if (vectorTableName == null) vectorTableName = "document_vectors";
	}
}
