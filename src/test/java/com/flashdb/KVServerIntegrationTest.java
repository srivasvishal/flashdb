package com.flashdb;

import com.flashdb.client.KVClient;
import com.flashdb.server.KVServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test: real server + real client over Netty.
 * Proves the full stack (protocol, networking, storage) works together.
 */
class KVServerIntegrationTest {

    private KVServer server;
    private KVClient client;
    private Path dataDir;
    private static final int PORT = 19500;

    @BeforeEach
    void setUp() throws Exception {
        dataDir = Files.createTempDirectory("flashdb-itest");
        server = new KVServer(PORT, dataDir);
        CountDownLatch ready = new CountDownLatch(1);
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
                ready.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-server");
        serverThread.start();
        assertTrue(ready.await(5, TimeUnit.SECONDS), "Server should start within 5s");
        Thread.sleep(200); // allow bind to complete

        client = new KVClient("localhost", PORT);
        client.connect();
        client.setTimeoutSeconds(5);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (client != null) client.close();
        if (server != null) server.stop();
        if (dataDir != null && Files.exists(dataDir)) {
            Files.walk(dataDir).sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
    }

    @Test
    void putAndGet() throws Exception {
        client.put("hello", "world".getBytes());
        byte[] v = client.get("hello");
        assertNotNull(v);
        assertEquals("world", new String(v));
    }

    @Test
    void getNotFound() throws Exception {
        assertNull(client.get("nonexistent"));
    }

    @Test
    void putOverwriteAndDelete() throws Exception {
        client.put("key", "v1".getBytes());
        assertEquals("v1", new String(client.get("key")));
        client.put("key", "v2".getBytes());
        assertEquals("v2", new String(client.get("key")));
        client.del("key");
        assertNull(client.get("key"));
    }

    @Test
    void multipleKeys() throws Exception {
        for (int i = 0; i < 50; i++) {
            client.put("k" + i, ("value" + i).getBytes());
        }
        for (int i = 0; i < 50; i++) {
            assertEquals("value" + i, new String(client.get("k" + i)));
        }
    }
}
