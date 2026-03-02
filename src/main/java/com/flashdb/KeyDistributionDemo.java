package com.flashdb;

import com.flashdb.distributed.HashRing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo: Shows which keys land on which node (consistent hashing in action).
 * Good for presentations - illustrates data distribution across the cluster.
 *
 * Run: mvn exec:java -Pkey-distribution
 */
public class KeyDistributionDemo {

    private static final List<String> SERVERS = List.of("node1:8080", "node2:8081", "node3:8082");
    private static final String[] SAMPLE_KEYS = {
            "passenger:1", "passenger:42", "passenger:306", "user:alice", "user:bob",
            "session:xyz", "config:region", "cache:home", "order:1001", "product:SKU-99"
    };

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  FlashDB - Key Distribution (Consistent Hashing)             ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("  Nodes: " + SERVERS);
        System.out.println();
        System.out.println("  Key → Primary Node");
        System.out.println("  ─────────────────");

        HashRing ring = new HashRing();
        SERVERS.forEach(ring::addServer);

        Map<String, List<String>> nodeToKeys = new LinkedHashMap<>();
        SERVERS.forEach(s -> nodeToKeys.put(s, new ArrayList<>()));

        for (String key : SAMPLE_KEYS) {
            String node = ring.getServer(key);
            nodeToKeys.get(node).add(key);
            System.out.println("    " + key + "  →  " + node);
        }

        System.out.println();
        System.out.println("  Summary (keys per node):");
        nodeToKeys.forEach((node, keys) ->
                System.out.println("    " + node + ": " + keys.size() + " keys"));
        System.out.println();
        System.out.println("  ✓ Same key always maps to same node (deterministic)");
        System.out.println("  ✓ Adding/removing nodes causes minimal key movement");
    }
}
