package com.flashdb;

import com.flashdb.compaction.CompactionTask;
import com.flashdb.server.KVServer;

import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * Main entry point for FlashDB server.
 * Usage: java FlashDBMain [port] [dataDir]
 * Default: port 8080, dataDir ./data
 */
public class FlashDBMain {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        Path dataDir = args.length > 1 ? Path.of(args[1]) : Path.of("data");

        KVServer server = new KVServer(port, dataDir);
        server.start();

        // Start background compaction
        var compaction = new CompactionTask(server.getStorage(), dataDir);
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "compaction");
            t.setDaemon(true);
            return t;
        }).execute(compaction);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            compaction.stop();
            server.stop();
        }));

        Thread.currentThread().join();
    }
}
