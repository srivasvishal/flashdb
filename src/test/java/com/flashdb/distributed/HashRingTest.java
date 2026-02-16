package com.flashdb.distributed;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HashRingTest {

    @Test
    void addAndGetServer() {
        HashRing ring = new HashRing();
        ring.addServer("localhost:8080");
        ring.addServer("localhost:8081");
        ring.addServer("localhost:8082");

        String server = ring.getServer("key1");
        assertNotNull(server);
        assertTrue(Set.of("localhost:8080", "localhost:8081", "localhost:8082").contains(server));
    }

    @Test
    void getReplicaServers() {
        HashRing ring = new HashRing();
        ring.addServer("localhost:8080");
        ring.addServer("localhost:8081");
        ring.addServer("localhost:8082");

        List<String> replicas = ring.getReplicaServers("key1", 2);
        assertEquals(2, replicas.size());
        assertNotEquals(replicas.get(0), replicas.get(1));
    }

    @Test
    void removeServer() {
        HashRing ring = new HashRing();
        ring.addServer("localhost:8080");
        ring.addServer("localhost:8081");
        ring.removeServer("localhost:8080");

        Set<String> servers = ring.getServers();
        assertEquals(1, servers.size());
        assertTrue(servers.contains("localhost:8081"));
    }
}
