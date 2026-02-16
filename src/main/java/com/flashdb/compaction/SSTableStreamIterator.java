package com.flashdb.compaction;

import com.flashdb.storage.SSTable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Streaming iterator over SSTable entries - reads sequentially without full load.
 */
public class SSTableStreamIterator implements Iterator<Map.Entry<String, byte[]>> {

    private final FileChannel channel;
    private final ByteBuffer buf = ByteBuffer.allocate(65536);
    private Map.Entry<String, byte[]> nextEntry;
    private boolean exhausted;

    public SSTableStreamIterator(Path path) throws IOException {
        this.channel = FileChannel.open(path, StandardOpenOption.READ);
        ByteBuffer header = ByteBuffer.allocate(4);
        channel.read(header);
        header.flip();
        int count = header.getInt();
        exhausted = count == 0;
        advance();
    }

    private void advance() {
        if (exhausted) return;
        try {
            while (true) {
                if (buf.remaining() < 4) {
                    buf.compact();
                    if (channel.read(buf) <= 0) {
                        exhausted = true;
                        nextEntry = null;
                        return;
                    }
                    buf.flip();
                }
                int keyLen = buf.getInt();
                if (buf.remaining() < keyLen + 4) {
                    buf.position(buf.position() - 4);
                    buf.compact();
                    if (channel.read(buf) <= 0) {
                        exhausted = true;
                        nextEntry = null;
                        return;
                    }
                    buf.flip();
                    buf.position(buf.position() - 4);
                    continue;
                }
                byte[] keyBytes = new byte[keyLen];
                buf.get(keyBytes);
                String key = new String(keyBytes);
                int valueLen = buf.getInt();
                if (buf.remaining() < valueLen) {
                    buf.position(buf.position() - 4 - keyLen);
                    buf.compact();
                    if (channel.read(buf) <= 0) {
                        exhausted = true;
                        nextEntry = null;
                        return;
                    }
                    buf.flip();
                    buf.position(buf.position() - 4 - keyLen);
                    continue;
                }
                byte[] value = new byte[valueLen];
                buf.get(value);
                nextEntry = new AbstractMap.SimpleEntry<>(key, value);
                return;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasNext() {
        return nextEntry != null;
    }

    @Override
    public Map.Entry<String, byte[]> next() {
        if (nextEntry == null) throw new NoSuchElementException();
        Map.Entry<String, byte[]> current = nextEntry;
        advance();
        return current;
    }

    public void close() throws IOException {
        channel.close();
    }
}
