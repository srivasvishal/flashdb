package com.flashdb.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StorageEngineTest {

    private Path tempDir;
    private StorageEngine engine;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("flashdb-test");
        engine = new StorageEngine(tempDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (engine != null) engine.close();
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir).sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
    }

    @Test
    void putAndGet() throws IOException {
        engine.put("key1", "value1".getBytes());
        byte[] result = engine.get("key1");
        assertNotNull(result);
        assertEquals("value1", new String(result));
    }

    @Test
    void getNotFound() throws IOException {
        assertNull(engine.get("nonexistent"));
    }

    @Test
    void del() throws IOException {
        engine.put("key1", "value1".getBytes());
        engine.del("key1");
        assertNull(engine.get("key1"));
    }

    @Test
    void persistence() throws IOException {
        engine.put("persist1", "data".getBytes());
        engine.close();

        StorageEngine engine2 = new StorageEngine(tempDir);
        byte[] result = engine2.get("persist1");
        assertNotNull(result);
        assertEquals("data", new String(result));
        engine2.close();
    }
}
