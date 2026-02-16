package com.flashdb.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Sorted String Table - immutable on-disk storage format.
 * Format: [entry_count (4 bytes)][key_len (4)][key][value_len (4)][value]... (repeated)
 * Keys are sorted for efficient binary search.
 */
public class SSTable {

    private final Path path;
    private final SparseIndex sparseIndex;
    private final int entryCount;

    public SSTable(Path path, SparseIndex sparseIndex, int entryCount) {
        this.path = path;
        this.sparseIndex = sparseIndex;
        this.entryCount = entryCount;
    }

    /**
     * Flush MemTable to disk as an SSTable file.
     */
    public static SSTable flush(ConcurrentSkipListMap<String, byte[]> data, Path dataDir) throws IOException {
        long timestamp = System.currentTimeMillis();
        Path sstPath = dataDir.resolve("data-" + timestamp + ".sst");
        Files.createDirectories(dataDir);

        try (FileChannel channel = FileChannel.open(sstPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            ByteBuffer header = ByteBuffer.allocate(4);
            header.putInt(data.size());
            header.flip();
            channel.write(header);

            SparseIndex index = new SparseIndex(100); // Every 100th key
            long offset = 4; // After header
            int count = 0;

            for (Map.Entry<String, byte[]> entry : data.entrySet()) {
                String key = entry.getKey();
                byte[] value = entry.getValue();

                byte[] keyBytes = key.getBytes();
                ByteBuffer buf = ByteBuffer.allocate(4 + keyBytes.length + 4 + value.length);
                buf.putInt(keyBytes.length);
                buf.put(keyBytes);
                buf.putInt(value.length);
                buf.put(value);
                buf.flip();

                index.maybeAdd(key, offset, count);
                channel.write(buf);
                offset += buf.remaining();
                count++;
            }

            return new SSTable(sstPath, index, data.size());
        }
    }

    /**
     * Get value by key. Uses sparse index for fast lookup.
     */
    public byte[] get(String key) throws IOException {
        SparseIndex.IndexEntry range = sparseIndex.getRange(key);
        if (range == null) {
            return null;
        }

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.position(range.startOffset());

            ByteBuffer buf = ByteBuffer.allocate(8192);
            while (channel.read(buf) > 0) {
                buf.flip();
                while (buf.remaining() >= 4) {
                    int keyLen = buf.getInt();
                    if (buf.remaining() < keyLen + 4) {
                        buf.position(buf.position() - 4);
                        break;
                    }
                    byte[] keyBytes = new byte[keyLen];
                    buf.get(keyBytes);
                    String k = new String(keyBytes);
                    int cmp = key.compareTo(k);
                    if (cmp == 0) {
                        int valueLen = buf.getInt();
                        byte[] value = new byte[valueLen];
                        buf.get(value);
                        return value;
                    }
                    if (cmp < 0) {
                        return null; // Passed the key
                    }
                    int valueLen = buf.getInt();
                    buf.position(buf.position() + valueLen);
                }
                buf.compact();
                if (range.endOffset() > 0 && channel.position() >= range.endOffset()) {
                    break;
                }
            }
        }
        return null;
    }

    public Path getPath() {
        return path;
    }

    public int getEntryCount() {
        return entryCount;
    }

    public SparseIndex getSparseIndex() {
        return sparseIndex;
    }

    public void delete() throws IOException {
        Files.deleteIfExists(path);
    }
}
