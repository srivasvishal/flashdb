package com.flashdb.storage;

import java.util.Map;
import java.util.TreeMap;

/**
 * Sparse index for SSTable - stores file offset for every Nth key.
 * Enables fast lookups without scanning the entire file.
 */
public class SparseIndex {

    private final int indexInterval;
    private final TreeMap<String, IndexEntry> index = new TreeMap<>();

    public SparseIndex(int indexInterval) {
        this.indexInterval = indexInterval;
    }

    public void maybeAdd(String key, long offset, int keyIndex) {
        if (keyIndex % indexInterval == 0) {
            index.put(key, new IndexEntry(offset, -1)); // endOffset set during merge
        }
    }

    /**
     * Get the byte range to search for the key.
     * Returns start offset and end offset (or -1 if to end of file).
     */
    public IndexEntry getRange(String key) {
        Map.Entry<String, IndexEntry> floor = index.floorEntry(key);
        if (floor == null) {
            return new IndexEntry(4, -1); // Start after header
        }
        Map.Entry<String, IndexEntry> ceiling = index.ceilingEntry(key);
        long start = floor.getValue().startOffset();
        long end = ceiling != null ? ceiling.getValue().startOffset() : -1;
        return new IndexEntry(start, end);
    }

    public record IndexEntry(long startOffset, long endOffset) {}
}
