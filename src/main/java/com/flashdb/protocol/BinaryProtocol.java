package com.flashdb.protocol;

import java.nio.ByteBuffer;

/**
 * Simple binary wire protocol.
 * Packet format: [OpCode (1 byte)][Key Length (4 bytes)][Key bytes][Value Length (4 bytes)][Value bytes]
 * For GET/DEL: Value Length and Value are 0.
 */
public class BinaryProtocol {

    public static final byte OP_PUT = 1;
    public static final byte OP_GET = 2;
    public static final byte OP_DEL = 3;

    public static final byte STATUS_OK = 1;
    public static final byte STATUS_NOT_FOUND = 2;
    public static final byte STATUS_ERROR = 3;

    public record Request(byte op, String key, byte[] value) {}

    public record Response(byte status, byte[] value) {}

    public static ByteBuffer encodeRequest(byte op, String key, byte[] value) {
        byte[] keyBytes = key.getBytes();
        int valueLen = value != null ? value.length : 0;
        int size = 1 + 4 + keyBytes.length + 4 + valueLen;
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.put(op);
        buf.putInt(keyBytes.length);
        buf.put(keyBytes);
        buf.putInt(valueLen);
        if (value != null && value.length > 0) {
            buf.put(value);
        }
        buf.flip();
        return buf;
    }

    public static ByteBuffer encodeResponse(byte status, byte[] value) {
        int valueLen = value != null ? value.length : 0;
        int size = 1 + 4 + valueLen;
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.put(status);
        buf.putInt(valueLen);
        if (value != null && value.length > 0) {
            buf.put(value);
        }
        buf.flip();
        return buf;
    }

    public static Request decodeRequest(ByteBuffer buf) {
        if (buf.remaining() < 5) return null;
        byte op = buf.get();
        int keyLen = buf.getInt();
        if (buf.remaining() < keyLen) return null;
        byte[] keyBytes = new byte[keyLen];
        buf.get(keyBytes);
        String key = new String(keyBytes);

        byte[] value = null;
        if (buf.remaining() >= 4) {
            int valueLen = buf.getInt();
            if (valueLen > 0 && buf.remaining() >= valueLen) {
                value = new byte[valueLen];
                buf.get(value);
            }
        }
        return new Request(op, key, value);
    }

    public static Response decodeResponse(ByteBuffer buf) {
        if (buf.remaining() < 5) return null;
        byte status = buf.get();
        int valueLen = buf.getInt();
        byte[] value = null;
        if (valueLen > 0 && buf.remaining() >= valueLen) {
            value = new byte[valueLen];
            buf.get(value);
        }
        return new Response(status, value);
    }
}
