package com.flashdb;

import com.flashdb.client.KVClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Real-world demo: Loads Titanic passenger data from open source (DataScience Dojo / GitHub)
 * and demonstrates FlashDB as a passenger record cache for disaster recovery dashboards.
 *
 * Use case: Fast key-value lookups for incident response systems.
 * Run server first: mvn exec:java
 * Then: mvn exec:java -Preal-data
 */
public class RealDataDemo {

    private static final String TITANIC_CSV_URL =
            "https://raw.githubusercontent.com/datasciencedojo/datasets/master/titanic.csv";

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  FlashDB - Real Data Demo                                    ║");
        System.out.println("║  Use Case: Passenger Record Cache (Titanic Dataset)            ║");
        System.out.println("║  Source: DataScience Dojo / GitHub (open source)              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("  ⚠  REQUIRED: Start the server first in another terminal:");
        System.out.println("      mvn exec:java");
        System.out.println();

        // Fetch or load data
        List<String[]> records = loadData();
        if (records.isEmpty()) {
            System.err.println("No data loaded. Check network or use bundled sample.");
            System.exit(1);
        }

        System.out.println("  Loaded " + records.size() + " passenger records");
        System.out.println();

        // Connect to FlashDB
        KVClient client = new KVClient("localhost", 8080);
        client.setTimeoutSeconds(60);
        try {
            System.out.print("  Connecting to FlashDB server... ");
            client.connect();
            client.put("__ping__", "ok".getBytes());
            byte[] pong = client.get("__ping__");
            if (pong == null || !"ok".equals(new String(pong))) throw new RuntimeException("Ping failed");
            System.out.println("OK");
        } catch (Exception e) {
            System.err.println("FAILED");
            System.err.println();
            System.err.println("  Start the server first: mvn exec:java");
            System.exit(1);
        }
        System.out.println();

        // Load into FlashDB
        System.out.println("  Loading records into FlashDB...");
        long start = System.currentTimeMillis();
        String[] headers = records.get(0);
        int loaded = 0;
        try {
            for (int i = 1; i < records.size(); i++) {
                String[] row = records.get(i);
                if (row.length < headers.length) continue;
                String json = toJson(headers, row);
                String key = "passenger:" + row[0]; // PassengerId
                client.put(key, json.getBytes(StandardCharsets.UTF_8));
                loaded++;
                if (loaded == 1) System.out.println("  First record OK, continuing...");
            }
        } catch (java.util.concurrent.TimeoutException e) {
            System.err.println();
            System.err.println("  ✗ Timeout while loading. Is the server running?");
            System.err.println();
            System.err.println("  Start the server FIRST in another terminal:");
            System.err.println("    mvn exec:java");
            System.err.println();
            System.err.println("  Then run this demo again.");
            System.exit(1);
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  ✓ Loaded " + loaded + " records in " + elapsed + " ms");
        System.out.println("  Throughput: " + (loaded * 1000L / Math.max(1, elapsed)) + " writes/sec");
        System.out.println();

        // Demo queries
        System.out.println("  ─── Sample Queries (Real Data) ───");
        System.out.println();

        byte[] p1 = client.get("passenger:1");
        if (p1 != null) {
            System.out.println("  GET passenger:1 (Owen Harris Braund):");
            System.out.println("    " + new String(p1).replace("\"", "").substring(0, Math.min(80, p1.length)) + "...");
        }

        byte[] p306 = client.get("passenger:306");
        if (p306 != null) {
            System.out.println();
            System.out.println("  GET passenger:306 (Hudson Trevor Allison):");
            System.out.println("    " + new String(p306).replace("\"", ""));
        }

        // Survival stats (sample)
        int survived = 0, total = 0;
        for (int id : new int[]{1, 2, 3, 10, 50, 100, 200, 300, 400, 500}) {
            byte[] v = client.get("passenger:" + id);
            if (v != null) {
                total++;
                if (new String(v).contains("\"Survived\":1")) survived++;
            }
        }
        System.out.println();
        System.out.println("  Sample survival check (10 passengers): " + survived + "/" + total + " survived");
        System.out.println();

        // Update demo
        System.out.println("  ─── Update Demo ───");
        client.put("passenger:1", "{\"PassengerId\":1,\"Survived\":0,\"Pclass\":3,\"Name\":\"Braund, Mr. Owen Harris\",\"Note\":\"Record updated for analysis\"}".getBytes());
        System.out.println("  Updated passenger:1 with analysis note");
        byte[] updated = client.get("passenger:1");
        System.out.println("  GET passenger:1: " + (updated != null ? new String(updated).substring(0, 60) + "..." : "null"));
        System.out.println();

        // Delete demo
        client.put("passenger:999", "{\"test\":\"temporary\"}".getBytes());
        client.del("passenger:999");
        System.out.println("  Deleted test record passenger:999");
        System.out.println("  GET passenger:999: " + (client.get("passenger:999") == null ? "null (deleted)" : "exists"));
        System.out.println();

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  Demo complete. Data persists in FlashDB (WAL + SSTables).   ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        client.close();
    }

    private static List<String[]> loadData() throws Exception {
        // Try to fetch from URL first
        try {
            HttpClient http = HttpClient.newBuilder().build();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(TITANIC_CSV_URL)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                return parseCsv(resp.body());
            }
        } catch (Exception e) {
            System.out.println("  Network fetch failed, trying bundled sample...");
        }

        // Fallback: bundled sample in resources
        try (var in = RealDataDemo.class.getResourceAsStream("/titanic_sample.csv")) {
            if (in != null) {
                String csv = new BufferedReader(new InputStreamReader(in)).lines().collect(Collectors.joining("\n"));
                return parseCsv(csv);
            }
        }

        return List.of();
    }

    private static List<String[]> parseCsv(String csv) {
        List<String[]> rows = new ArrayList<>();
        for (String line : csv.split("\\r?\\n")) {
            if (line.isBlank()) continue;
            List<String> fields = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean inQuotes = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '"') {
                    inQuotes = !inQuotes;
                } else if (inQuotes) {
                    field.append(c);
                } else if (c == ',') {
                    fields.add(field.toString().trim());
                    field.setLength(0);
                } else {
                    field.append(c);
                }
            }
            fields.add(field.toString().trim());
            rows.add(fields.toArray(new String[0]));
        }
        return rows;
    }

    private static String toJson(String[] headers, String[] values) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < Math.min(headers.length, values.length); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(headers[i]).append("\":");
            String v = values[i];
            if (v.matches("\\d+") || v.matches("\\d+\\.\\d+")) {
                sb.append(v);
            } else {
                sb.append("\"").append(v.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
