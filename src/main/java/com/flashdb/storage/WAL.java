package com.flashdb.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Write-Ahead Log for durability.
 * Before adding to MemTable, operations are appended to wal.log.
 * On restart, the log is replayed to restore state.
 */
public class WAL {

    private static final byte OP_PUT = 1;
    private static final byte OP_DEL = 2;

    private final Path walPath;
    private FileChannel channel;
    private volatile boolean closed;

    public WAL(Path dataDir) throws IOException {
        this.walPath = dataDir.resolve("wal.log");
        Files.createDirectories(dataDir);
        this.channel = FileChannel.open(walPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);
        this.closed = false;
    }

    public synchronized void put(String key, byte[] value) throws IOException {
        if (closed) throw new IllegalStateException("WAL is closed");
        ByteBuffer buf = encodePut(key, value);
        channel.position(channel.size());
        channel.write(buf);
        channel.force(true);
    }

    public synchronized void delete(String key) throws IOException {
        if (closed) throw new IllegalStateException("WAL is closed");
        ByteBuffer buf = encodeDel(key);
        channel.position(channel.size());
        channel.write(buf);
        channel.force(true);
    }

    /**
     * Replay the WAL to restore MemTable state.
     */
    public void replay(MemTable memTable) throws IOException {
        if (!Files.exists(walPath) || Files.size(walPath) == 0) {
            return;
        }
        try (FileChannel readChannel = FileChannel.open(walPath, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(1024 * 1024); // 1MB buffer
            while (readChannel.read(buf) > 0) {
                buf.flip();
                while (buf.remaining() >= 5) { // min: 1 byte op + 4 byte key len
                    byte op = buf.get();
                    int keyLen = buf.getInt();
                    if (buf.remaining() < keyLen) {
                        buf.position(buf.position() - 5);
                        break;
                    }
                    byte[] keyBytes = new byte[keyLen];
                    buf.get(keyBytes);
                    String key = new String(keyBytes);

                    if (op == OP_PUT) {
                        if (buf.remaining() < 4) break;
                        int valueLen = buf.getInt();
                        if (buf.remaining() < valueLen) {
                            buf.position(buf.position() - 4 - keyLen - 5);
                            break;
                        }
                        byte[] value = new byte[valueLen];
                        buf.get(value);
                        memTable.put(key, value);
                    } else if (op == OP_DEL) {
                        memTable.remove(key);
                    }
                }
                buf.compact();
            }
        }
    }

    public synchronized void rotate(Path newWalPath) throws IOException {
        close();
        Files.move(walPath, newWalPath);
        this.channel = FileChannel.open(walPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);
        this.closed = false;
    }

    public synchronized void close() throws IOException {
        if (!closed) {
            channel.force(true);
            channel.close();
            closed = true;
        }
    }

    public Path getWalPath() {
        return walPath;
    }

    private ByteBuffer encodePut(String key, byte[] value) {
        byte[] keyBytes = key.getBytes();
        int size = 1 + 4 + keyBytes.length + 4 + value.length;
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.put(OP_PUT);
        buf.putInt(keyBytes.length);
        buf.put(keyBytes);
        buf.putInt(value.length);
        buf.put(value);
        buf.flip();
        return buf;
    }

    private ByteBuffer encodeDel(String key) {
        byte[] keyBytes = key.getBytes();
        int size = 1 + 4 + keyBytes.length;
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.put(OP_DEL);
        buf.putInt(keyBytes.length);
        buf.put(keyBytes);
        buf.flip();
        return buf;
    }

}
