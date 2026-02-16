package com.flashdb.compaction;

import com.flashdb.storage.SSTable;
import com.flashdb.storage.SparseIndex;
import com.flashdb.storage.StorageEngine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Background compaction: merges 4-5 old SSTables into one new SSTable.
 * Uses merge-sort style iteration. Runs in parallel while DB serves reads/writes.
 */
public class CompactionTask implements Runnable {

    private static final int SSTABLES_TO_MERGE = 4;
    private static final int TOMBSTONE = 0;

    private final StorageEngine storage;
    private final Path dataDir;
    private volatile boolean running = true;

    public CompactionTask(StorageEngine storage, Path dataDir) {
        this.storage = storage;
        this.dataDir = dataDir;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(60_000); // Run every minute
                compact();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void compact() throws IOException {
        List<SSTable> sstables = storage.getSstables();
        if (sstables.size() < SSTABLES_TO_MERGE) return;

        List<SSTable> toMerge = sstables.stream()
                .sorted(Comparator.comparing(s -> s.getPath().getFileName().toString()))
                .limit(SSTABLES_TO_MERGE)
                .toList();

        if (toMerge.size() < 2) return;

        SSTable merged = mergeSSTables(toMerge);
        storage.replaceSSTables(toMerge, merged);
    }

    private SSTable mergeSSTables(List<SSTable> tables) throws IOException {
        Path mergedPath = dataDir.resolve("data-merged-" + System.currentTimeMillis() + ".sst");
        Files.createDirectories(dataDir);

        List<SSTableStreamIterator> streamIters = new ArrayList<>();
        List<PeekingIterator<Map.Entry<String, byte[]>>> iterators = new ArrayList<>();
        for (SSTable sst : tables) {
            SSTableStreamIterator si = new SSTableStreamIterator(sst.getPath());
            streamIters.add(si);
            iterators.add(new PeekingIteratorImpl(si));
        }

        PriorityQueue<PeekingIterator<Map.Entry<String, byte[]>>> heap = new PriorityQueue<>(
                Comparator.comparing(it -> it.peek().getKey())
        );

        for (PeekingIterator<Map.Entry<String, byte[]>> it : iterators) {
            if (it.hasNext()) heap.add(it);
        }

        SparseIndex index = new SparseIndex(100);
        int count = 0;
        long offset = 4;

        try (FileChannel channel = FileChannel.open(mergedPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            String lastKey = null;
            while (!heap.isEmpty()) {
                PeekingIterator<Map.Entry<String, byte[]>> it = heap.poll();
                Map.Entry<String, byte[]> entry = it.next();

                if (entry.getKey().equals(lastKey)) continue;
                lastKey = entry.getKey();
                if (isTombstone(entry.getValue())) continue;

                byte[] keyBytes = entry.getKey().getBytes();
                byte[] value = entry.getValue();
                ByteBuffer buf = ByteBuffer.allocate(4 + keyBytes.length + 4 + value.length);
                buf.putInt(keyBytes.length);
                buf.put(keyBytes);
                buf.putInt(value.length);
                buf.put(value);
                buf.flip();
                channel.write(buf);
                index.maybeAdd(entry.getKey(), offset, count);
                offset += buf.remaining();
                count++;

                if (it.hasNext()) heap.add(it);
            }

            ByteBuffer header = ByteBuffer.allocate(4);
            header.putInt(count);
            header.flip();
            channel.position(0);
            channel.write(header);
        }

        for (SSTableStreamIterator si : streamIters) {
            si.close();
        }

        return new SSTable(mergedPath, index, count);
    }

    private static boolean isTombstone(byte[] value) {
        return value != null && value.length == 1 && value[0] == TOMBSTONE;
    }

    interface PeekingIterator<T> extends Iterator<T> {
        T peek();
    }

    private static class PeekingIteratorImpl implements PeekingIterator<Map.Entry<String, byte[]>> {
        private final SSTableStreamIterator inner;
        private Map.Entry<String, byte[]> next;

        PeekingIteratorImpl(SSTableStreamIterator inner) {
            this.inner = inner;
            this.next = inner.hasNext() ? inner.next() : null;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public Map.Entry<String, byte[]> next() {
            Map.Entry<String, byte[]> current = next;
            next = inner.hasNext() ? inner.next() : null;
            return current;
        }

        @Override
        public Map.Entry<String, byte[]> peek() {
            return next;
        }
    }
}
