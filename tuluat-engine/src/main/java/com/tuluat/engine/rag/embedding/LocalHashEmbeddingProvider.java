package com.tuluat.engine.rag.embedding;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Deterministic local embedding provider (ADR 008): bag of character n-gram
 * hashes mapped onto a 1536-dimension vector via a seeded PRNG. Reproducible
 * across runs and JVMs — suited to dev, CI, and E2E where external embedding
 * APIs must not be required. Not semantically strong; production should
 * configure {@code SpringAiEmbeddingProvider}.
 */
@Component
public class LocalHashEmbeddingProvider implements EmbeddingProvider {

	public static final int DEFAULT_DIMENSION = 1536;

	private final int dimension;

	public LocalHashEmbeddingProvider() {
		this(DEFAULT_DIMENSION);
	}

	public LocalHashEmbeddingProvider(
			@org.springframework.beans.factory.annotation.Value("${tuluat.rag.embedding-dimension:1536}") int dimension) {
		this.dimension = dimension;
	}

	@Override
	public int dimension() {
		return dimension;
	}

	@Override
	public float[] embed(String text) {
		float[] vector = new float[dimension];
		if (text == null || text.isBlank()) {
			return vector;
		}
		String normalized = text.toLowerCase().replaceAll("\\s+", " ").trim();
		byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);

		// Bag of character 4-grams: hash each n-gram into one dimension bucket.
		for (int i = 0; i + 4 <= bytes.length; i++) {
			int h = 0;
			for (int j = 0; j < 4; j++) {
				h = 31 * h + (bytes[i + j] & 0xFF);
			}
			int bucket = Math.floorMod(h, dimension);
			vector[bucket] += 1.0f;
		}
		// Word presence boost: map each word hash to a bucket as well.
		for (String word : normalized.split(" ")) {
			if (word.isBlank()) {
				continue;
			}
			int h = word.hashCode();
			int bucket = Math.floorMod(h, dimension);
			vector[bucket] += 3.0f;
		}
		return normalize(vector);
	}

	private float[] normalize(float[] v) {
		double norm = 0.0;
		for (float f : v) {
			norm += f * f;
		}
		if (norm == 0.0) {
			return v;
		}
		double inv = 1.0 / Math.sqrt(norm);
		for (int i = 0; i < v.length; i++) {
			v[i] *= (float) inv;
		}
		return v;
	}
}
