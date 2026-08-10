package com.tuluat.engine.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive character chunker: splits on paragraph, newline, sentence, and word
 * boundaries, falling back to hard character splits. Chunks are sized to
 * {@link ChunkConfig#chunkSize()} with {@link ChunkConfig#overlap()} overlap.
 */
@Component
public class RecursiveCharacterChunker implements Chunker {

	/**
	 * Separators tried in order: paragraph, newline, sentence, word, then hard
	 * split.
	 */
	private static final String[] SEPARATORS = {"\n\n", "\n", ". ", " ", ""};

	@Override
	public List<TextChunk> chunk(String content, String sourceRef, ChunkConfig config) {
		if (content == null || content.isBlank()) {
			return List.of();
		}
		List<TextChunk> chunks = new ArrayList<>();
		split(content, 0, sourceRef, config, chunks);
		return chunks;
	}

	private void split(String text, int baseIndex, String sourceRef, ChunkConfig config, List<TextChunk> out) {
		if (text.length() <= config.chunkSize()) {
			out.add(new TextChunk(out.size(), text.trim(), sourceRef));
			return;
		}
		String separator = bestSeparator(text, config.chunkSize());
		int splitAt = findSplitPoint(text, separator, config.chunkSize());

		String left = text.substring(0, splitAt).trim();
		if (!left.isBlank()) {
			out.add(new TextChunk(out.size(), left, sourceRef));
		}
		// Overlap: keep the last `overlap` chars of the left part as context
		int restart = Math.max(0, splitAt - config.overlap());
		String right = text.substring(restart);
		if (!right.isBlank()) {
			split(right, baseIndex + splitAt, sourceRef, config, out);
		}
	}

	private String bestSeparator(String text, int chunkSize) {
		for (String sep : SEPARATORS) {
			if (sep.isEmpty()) {
				return sep;
			}
			if (text.indexOf(sep) >= 0 && text.indexOf(sep) < chunkSize) {
				return sep;
			}
		}
		return "";
	}

	private int findSplitPoint(String text, String separator, int chunkSize) {
		if (separator.isEmpty()) {
			return Math.min(chunkSize, text.length());
		}
		int idx = text.indexOf(separator, chunkSize / 2);
		if (idx < 0 || idx > chunkSize) {
			idx = text.lastIndexOf(separator, chunkSize);
		}
		if (idx <= 0) {
			return Math.min(chunkSize, text.length());
		}
		return idx + separator.length();
	}
}
