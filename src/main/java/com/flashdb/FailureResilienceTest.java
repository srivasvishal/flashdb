package com.flashdb;

import com.flashdb.distributed.DistributedKVClient;

import java.util.List;
import java.util.Scanner;

/**
 * Manual test for read-from-replica fallback when a node fails.
 *
 * Steps:
 *   1. Start 3-node cluster (8080, 8081, 8082)
 *   2. mvn exec:java -Presilience-test
 *   3. When prompted, kill one node (Ctrl+C in its terminal)
 *   4. Press Enter — GETs should succeed from replicas
 */
public class FailureResilienceTest {

    private static final List<String> SERVERS =
            List.of("localhost:8080", "localhost:8081", "localhost:8082");
    private static final int KEY_COUNT = 100;
    private static final int REPLICATION = 2;

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  FlashDB - Failure Resilience Test (Read-from-Replica)      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("  This test verifies GET succeeds from replicas when primary is down.");
        System.out.println();

        try (DistributedKVClient client = new DistributedKVClient(SERVERS, REPLICATION)) {
            client.setTimeoutSeconds(2); // Fail fast when primary is down (avoid long hang per key)
            // Phase 1: Load data with all nodes up
            System.out.println("  Phase 1: Loading " + KEY_COUNT + " keys (all nodes up)...");
            byte[] value = "test-value".getBytes();
            for (int i = 0; i < KEY_COUNT; i++) {
                client.put("resilience:" + i, value);
            }
            System.out.println("  ✓ Data loaded");
            System.out.println();

            // Phase 2: User kills a node
            System.out.println("  Phase 2: NOW kill one node (Ctrl+C in its terminal)");
            System.out.println("           e.g. stop the server on 8080, 8081, or 8082");
            System.out.println();
            System.out.print("  Press Enter when the node is down... ");
            new Scanner(System.in).nextLine();
            System.out.println();

            // Phase 3: GET all keys — should succeed from replicas
            System.out.println("  Phase 3: GETting all keys (some primaries may be down)...");
            int success = 0;
            int failed = 0;
            for (int i = 0; i < KEY_COUNT; i++) {
                try {
                    byte[] v = client.get("resilience:" + i);
                    success++;
                } catch (Exception e) {
                    failed++;
                    System.err.println("    GET resilience:" + i + " failed: " + e.getMessage());
                }
                if ((i + 1) % 10 == 0) {
                    System.out.print(".");
                    System.out.flush();
                }
            }
            System.out.println();
            System.out.println();
            System.out.println("  Results: " + success + " succeeded, " + failed + " failed");
            if (failed == 0) {
                System.out.println("  ✓ Read-from-replica fallback is working!");
            } else {
                System.out.println("  ✗ Some GETs failed (replication factor may need to be 3 for 3 nodes)");
            }
        }
    }
}
