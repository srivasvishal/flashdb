package com.flashdb;

import com.flashdb.distributed.DistributedKVClient;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Latency benchmark for multi-node FlashDB cluster.
 * Measures GET and PUT latency percentiles (p50, p95, p99) and throughput.
 *
 * Usage:
 *   1. Start cluster nodes (see README "Run Distributed Cluster")
 *   2. mvn exec:java -Platency-benchmark
 *   3. Optional args: servers [default: localhost:8080,localhost:8081,localhost:8082]
 *                    --ops 5000 --warmup 500 --replication 2
 */
public class LatencyBenchmark {

    private static final List<String> DEFAULT_SERVERS =
            List.of("localhost:8080", "localhost:8081", "localhost:8082");
    private static final int DEFAULT_OPS = 5000;
    private static final int DEFAULT_WARMUP = 500;
    private static final int DEFAULT_REPLICATION = 2;
    private static final int KEY_SPACE = 10_000;
    private static final int VALUE_SIZE = 256;

    public static void main(String[] args) throws Exception {
        List<String> servers = DEFAULT_SERVERS;
        int ops = DEFAULT_OPS;
        int warmup = DEFAULT_WARMUP;
        int replication = DEFAULT_REPLICATION;

        for (int i = 0; i < args.length; i++) {
            if ("--ops".equals(args[i]) && i + 1 < args.length) {
                ops = Integer.parseInt(args[++i]);
            } else if ("--warmup".equals(args[i]) && i + 1 < args.length) {
                warmup = Integer.parseInt(args[++i]);
            } else if ("--replication".equals(args[i]) && i + 1 < args.length) {
                replication = Integer.parseInt(args[++i]);
            } else if (!args[i].startsWith("--")) {
                servers = List.of(args[i].split(","));
            }
        }

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  FlashDB - Multi-Node Latency Benchmark                     ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("  Servers:       " + servers);
        System.out.println("  Replication:   " + replication);
        System.out.println("  Warmup ops:    " + warmup);
        System.out.println("  Measure ops:   " + ops);
        System.out.println();

        try (DistributedKVClient client = new DistributedKVClient(servers, replication)) {

            // Warmup
            System.out.print("  Warming up... ");
            byte[] value = new byte[VALUE_SIZE];
            ThreadLocalRandom.current().nextBytes(value);
            for (int i = 0; i < warmup; i++) {
                String key = "bench:" + ThreadLocalRandom.current().nextInt(KEY_SPACE);
                client.put(key, value);
            }
            System.out.println("done");

            // Measure GET latency
            long[] getLatencies = new long[ops];
            long getStart = System.nanoTime();
            for (int i = 0; i < ops; i++) {
                String key = "bench:" + ThreadLocalRandom.current().nextInt(KEY_SPACE);
                long t0 = System.nanoTime();
                client.get(key);
                getLatencies[i] = (System.nanoTime() - t0) / 1000; // microseconds
            }
            long getElapsedMs = (System.nanoTime() - getStart) / 1_000_000;

            // Measure PUT latency
            long[] putLatencies = new long[ops];
            long putStart = System.nanoTime();
            for (int i = 0; i < ops; i++) {
                String key = "bench:" + (KEY_SPACE + ThreadLocalRandom.current().nextInt(KEY_SPACE));
                long t0 = System.nanoTime();
                client.put(key, value);
                putLatencies[i] = (System.nanoTime() - t0) / 1000; // microseconds
            }
            long putElapsedMs = (System.nanoTime() - putStart) / 1_000_000;

            // Print results
            System.out.println();
            System.out.println("  ─── Latency Results (µs) ───");
            System.out.println();
            printStats("GET", getLatencies, getElapsedMs, ops);
            System.out.println();
            printStats("PUT", putLatencies, putElapsedMs, ops);
            System.out.println();
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Benchmark complete.                                         ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
        }
    }

    private static void printStats(String op, long[] latencies, long elapsedMs, int ops) {
        Arrays.sort(latencies);
        long min = latencies[0];
        long max = latencies[latencies.length - 1];
        double avg = Arrays.stream(latencies).average().orElse(0);
        long p50 = latencies[(int) (latencies.length * 0.50)];
        long p95 = latencies[(int) (latencies.length * 0.95)];
        long p99 = latencies[(int) (latencies.length * 0.99)];

        System.out.println("  " + op + ":");
        System.out.println("    min: " + min + " µs   avg: " + String.format("%.1f", avg) + " µs   max: " + max + " µs");
        System.out.println("    p50: " + p50 + " µs   p95: " + p95 + " µs   p99: " + p99 + " µs");
        System.out.println("    throughput: " + (ops * 1000L / Math.max(1, elapsedMs)) + " ops/sec");
    }
}
