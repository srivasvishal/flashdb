package com.flashdb.protocol;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the binary wire protocol (encode/decode round-trip).
 */
class BinaryProtocolTest {

    @Test
    void putRequestRoundTrip() {
        ByteBuffer encoded = BinaryProtocol.encodeRequest(BinaryProtocol.OP_PUT, "user:42", "john@example.com".getBytes());
        encoded.position(0);
        BinaryProtocol.Request decoded = BinaryProtocol.decodeRequest(encoded);
        assertNotNull(decoded);
        assertEquals(BinaryProtocol.OP_PUT, decoded.op());
        assertEquals("user:42", decoded.key());
        assertArrayEquals("john@example.com".getBytes(), decoded.value());
    }

    @Test
    void getRequestRoundTrip() {
        ByteBuffer encoded = BinaryProtocol.encodeRequest(BinaryProtocol.OP_GET, "key", null);
        encoded.position(0);
        BinaryProtocol.Request decoded = BinaryProtocol.decodeRequest(encoded);
        assertNotNull(decoded);
        assertEquals(BinaryProtocol.OP_GET, decoded.op());
        assertEquals("key", decoded.key());
        assertNull(decoded.value());
    }

    @Test
    void delRequestRoundTrip() {
        ByteBuffer encoded = BinaryProtocol.encodeRequest(BinaryProtocol.OP_DEL, "tomb", null);
        encoded.position(0);
        BinaryProtocol.Request decoded = BinaryProtocol.decodeRequest(encoded);
        assertNotNull(decoded);
        assertEquals(BinaryProtocol.OP_DEL, decoded.op());
        assertEquals("tomb", decoded.key());
    }

    @Test
    void responseRoundTrip() {
        byte[] value = "some-value".getBytes();
        ByteBuffer encoded = BinaryProtocol.encodeResponse(BinaryProtocol.STATUS_OK, value);
        encoded.position(0);
        BinaryProtocol.Response decoded = BinaryProtocol.decodeResponse(encoded);
        assertNotNull(decoded);
        assertEquals(BinaryProtocol.STATUS_OK, decoded.status());
        assertArrayEquals(value, decoded.value());
    }

    @Test
    void notFoundResponseRoundTrip() {
        ByteBuffer encoded = BinaryProtocol.encodeResponse(BinaryProtocol.STATUS_NOT_FOUND, null);
        encoded.position(0);
        BinaryProtocol.Response decoded = BinaryProtocol.decodeResponse(encoded);
        assertNotNull(decoded);
        assertEquals(BinaryProtocol.STATUS_NOT_FOUND, decoded.status());
        assertNull(decoded.value());
    }
}
