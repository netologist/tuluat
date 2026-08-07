package com.tuluat.engine.rag;

import com.tuluat.engine.rag.storage.LocalObjectStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalObjectStorageTest {

    @TempDir
    Path tempDir;

    private LocalObjectStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalObjectStorage(tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        // temp dir cleaned by JUnit
    }

    @Test
    void putAndGetRoundTrip() {
        storage.put("documents/reports/a.txt", "hello rag".getBytes(StandardCharsets.UTF_8), "text/plain");
        Optional<LocalObjectStorage.StoredObject> obj = storage.get("documents/reports/a.txt");
        assertTrue(obj.isPresent());
        assertEquals("hello rag", new String(obj.get().content(), StandardCharsets.UTF_8));
        assertEquals("text/plain", obj.get().contentType());
    }

    @Test
    void getMissingReturnsEmpty() {
        assertFalse(storage.get("documents/missing.txt").isPresent());
    }

    @Test
    void listWithPrefix() {
        storage.put("documents/a/x.txt", new byte[]{1}, "text/plain");
        storage.put("documents/a/y.txt", new byte[]{2}, "text/plain");
        storage.put("documents/b/z.txt", new byte[]{3}, "text/plain");

        List<String> keys = storage.list("documents/a");
        assertEquals(List.of("documents/a/x.txt", "documents/a/y.txt"), keys);
    }

    @Test
    void deleteRemovesObject() {
        storage.put("documents/del.txt", new byte[]{1}, "text/plain");
        storage.delete("documents/del.txt");
        assertFalse(storage.get("documents/del.txt").isPresent());
    }

    @Test
    void rejectsPathTraversal() {
        assertThrows(LocalObjectStorage.StorageException.class, () ->
            storage.put("../evil.txt", new byte[]{1}, "text/plain"));
    }
}
