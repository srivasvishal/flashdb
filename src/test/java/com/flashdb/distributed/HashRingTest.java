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

    @Test
    void sameKeyConsistentlyMapsToSameServer() {
        HashRing ring = new HashRing();
        ring.addServer("localhost:8080");
        ring.addServer("localhost:8081");
        ring.addServer("localhost:8082");

        for (int i = 0; i < 10; i++) {
            assertEquals(ring.getServer("key1"), ring.getServer("key1"));
            assertEquals(ring.getServer("user:42"), ring.getServer("user:42"));
        }
    }

    @Test
    void addingNodeCausesMinimalKeyMovement() {
        HashRing ring = new HashRing();
        ring.addServer("n1");
        ring.addServer("n2");
        java.util.Map<String, String> before = new java.util.HashMap<>();
        for (int i = 0; i < 100; i++) {
            before.put("k" + i, ring.getServer("k" + i));
        }

        ring.addServer("n3");
        int moved = 0;
        for (int i = 0; i < 100; i++) {
            String after = ring.getServer("k" + i);
            if (!before.get("k" + i).equals(after)) moved++;
        }
        // With 3 nodes, ~1/3 of keys should move to new node (consistent hashing property)
        assertTrue(moved < 60, "Consistent hashing: most keys should stay on same server, moved=" + moved);
    }
}
