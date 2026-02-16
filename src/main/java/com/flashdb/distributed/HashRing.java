package com.flashdb.distributed;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Consistent Hash Ring with virtual nodes for even data distribution.
 * Keys are mapped to the first server found moving clockwise on the ring.
 */
public class HashRing {

    private static final int VIRTUAL_NODES_PER_SERVER = 150;
    private static final int RING_SIZE = 1 << 20; // 2^20 for hash space

    private final ConcurrentSkipListMap<Long, String> ring = new ConcurrentSkipListMap<>();
    private final Set<String> servers = ConcurrentHashMap.newKeySet();

    public HashRing() {}

    public void addServer(String serverId) {
        if (servers.add(serverId)) {
            for (int i = 0; i < VIRTUAL_NODES_PER_SERVER; i++) {
                long hash = hash(serverId + "#" + i);
                ring.put(hash, serverId);
            }
        }
    }

    public void removeServer(String serverId) {
        if (servers.remove(serverId)) {
            for (int i = 0; i < VIRTUAL_NODES_PER_SERVER; i++) {
                long hash = hash(serverId + "#" + i);
                ring.remove(hash);
            }
        }
    }

    /**
     * Get the primary server for a key (first server clockwise on the ring).
     */
    public String getServer(String key) {
        if (ring.isEmpty()) return null;
        long hash = hash(key);
        Map.Entry<Long, String> entry = ring.ceilingEntry(hash);
        return entry != null ? entry.getValue() : ring.firstEntry().getValue();
    }

    /**
     * Get N servers for replication (primary + next N-1 on the ring).
     */
    public List<String> getReplicaServers(String key, int replicationFactor) {
        if (ring.isEmpty()) return List.of();
        long hash = hash(key);
        List<String> result = new ArrayList<>();
        NavigableMap<Long, String> tail = ring.tailMap(hash, true);

        for (Map.Entry<Long, String> entry : tail.entrySet()) {
            if (result.size() >= replicationFactor) break;
            String server = entry.getValue();
            if (!result.contains(server)) {
                result.add(server);
            }
        }

        // Wrap around if needed
        if (result.size() < replicationFactor) {
            for (Map.Entry<Long, String> entry : ring.entrySet()) {
                if (result.size() >= replicationFactor) break;
                String server = entry.getValue();
                if (!result.contains(server)) {
                    result.add(server);
                }
            }
        }

        return result;
    }

    public Set<String> getServers() {
        return new HashSet<>(servers);
    }

    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            return ((digest[0] & 0xFFL) << 16 | (digest[1] & 0xFFL) << 8 | (digest[2] & 0xFFL)) % RING_SIZE;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
