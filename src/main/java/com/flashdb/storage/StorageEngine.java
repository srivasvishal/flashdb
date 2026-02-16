package com.flashdb.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * LSM Tree Storage Engine - the core of FlashDB.
 * Uses Read-Write Lock: writes lock MemTable, reads check MemTable -> Immutable MemTables -> SSTables.
 */
public class StorageEngine {

    private final Path dataDir;
    private final MemTable memTable;
    private final List<SSTable> sstables = new ArrayList<>();
    private WAL wal;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = rwLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();

    public StorageEngine(Path dataDir) throws IOException {
        this.dataDir = dataDir;
        this.memTable = new MemTable();
        this.wal = new WAL(dataDir);
        wal.replay(memTable);
    }

    public void put(String key, byte[] value) throws IOException {
        writeLock.lock();
        try {
            if (memTable.shouldFlush()) {
                switchMemTable();
            }
            wal.put(key, value);
            memTable.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }

    public byte[] get(String key) throws IOException {
        readLock.lock();
        try {
            // 1. Check active MemTable (tombstone = null)
            byte[] value = memTable.get(key);
            if (value != null) return isTombstone(value) ? null : value;

            // 2. Check SSTables (newest first - LSM order)
            for (int i = sstables.size() - 1; i >= 0; i--) {
                value = sstables.get(i).get(key);
                if (value != null) return isTombstone(value) ? null : value;
            }

            return null;
        } finally {
            readLock.unlock();
        }
    }

    /** Tombstone marker for deleted keys - when found during get, return null */
    private static final byte[] TOMBSTONE = new byte[]{0};

    public boolean del(String key) throws IOException {
        writeLock.lock();
        try {
            if (memTable.shouldFlush()) {
                switchMemTable();
            }
            wal.delete(key);
            memTable.put(key, TOMBSTONE); // Tombstone overwrites; get() returns null for tombstone
            return true; // del always "succeeds" - key is now logically deleted
        } finally {
            writeLock.unlock();
        }
    }

    private void switchMemTable() throws IOException {
        if (!memTable.shouldFlush()) return;

        ConcurrentSkipListMap<String, byte[]> snapshot = memTable.snapshot();
        memTable.clear();

        Path oldWal = dataDir.resolve("wal-" + System.currentTimeMillis() + ".log");
        wal.rotate(oldWal);

        SSTable sst = SSTable.flush(snapshot, dataDir);
        sstables.add(sst);
    }

    /**
     * Called by compaction to replace old SSTables with merged one.
     */
    public void replaceSSTables(List<SSTable> toRemove, SSTable newSst) throws IOException {
        writeLock.lock();
        try {
            sstables.removeAll(toRemove);
            for (SSTable sst : toRemove) {
                sst.delete();
            }
            sstables.add(newSst);
        } finally {
            writeLock.unlock();
        }
    }

    public List<SSTable> getSstables() {
        return new ArrayList<>(sstables);
    }

    private static boolean isTombstone(byte[] value) {
        return value.length == 1 && value[0] == 0;
    }

    public void close() throws IOException {
        wal.close();
    }
}
