package com.flashdb.storage;

import java.util.concurrent.ConcurrentSkipListMap;

/**
 * In-memory buffer for the LSM Tree.
 * Uses ConcurrentSkipListMap for thread-safe sorted key storage.
 * All writes go here first before being flushed to disk.
 */
public class MemTable {

    private static final int DEFAULT_MAX_SIZE_BYTES = 64 * 1024 * 1024; // 64MB

    private final ConcurrentSkipListMap<String, byte[]> data;
    private final int maxSizeBytes;
    private volatile int currentSizeBytes;

    public MemTable() {
        this(DEFAULT_MAX_SIZE_BYTES);
    }

    public MemTable(int maxSizeBytes) {
        this.data = new ConcurrentSkipListMap<>();
        this.maxSizeBytes = maxSizeBytes;
        this.currentSizeBytes = 0;
    }

    public void put(String key, byte[] value) {
        byte[] oldValue = data.put(key, value);
        if (oldValue != null) {
            currentSizeBytes -= (key.length() + oldValue.length + 8); // 8 for length overhead
        }
        currentSizeBytes += (key.length() + value.length + 8);
    }

    public byte[] get(String key) {
        return data.get(key);
    }

    public byte[] remove(String key) {
        byte[] removed = data.remove(key);
        if (removed != null) {
            currentSizeBytes -= (key.length() + removed.length + 8);
        }
        return removed;
    }

    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    public boolean shouldFlush() {
        return currentSizeBytes >= maxSizeBytes;
    }

    public int size() {
        return data.size();
    }

    public int getCurrentSizeBytes() {
        return currentSizeBytes;
    }

    /**
     * Returns a snapshot of the data for flushing. Does not clear the MemTable.
     */
    public ConcurrentSkipListMap<String, byte[]> snapshot() {
        return new ConcurrentSkipListMap<>(data);
    }

    /**
     * Clears the MemTable after a successful flush.
     */
    public void clear() {
        data.clear();
        currentSizeBytes = 0;
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }
}
