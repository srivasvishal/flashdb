package com.flashdb.server;

import com.flashdb.protocol.BinaryProtocol;
import com.flashdb.storage.StorageEngine;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Netty handler for KV operations. Offloads DB work to avoid blocking the event loop.
 */
public class KVServerHandler extends ChannelInboundHandlerAdapter {

    private final StorageEngine storage;
    private final java.util.concurrent.ExecutorService executor;

    public KVServerHandler(StorageEngine storage, java.util.concurrent.ExecutorService executor) {
        this.storage = storage;
        this.executor = executor;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        try {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);

            BinaryProtocol.Request req = BinaryProtocol.decodeRequest(buffer);
            if (req == null) {
                ctx.writeAndFlush(Unpooled.wrappedBuffer(new byte[]{BinaryProtocol.STATUS_ERROR, 0, 0, 0, 0}));
                return;
            }

            // Offload to executor (Virtual Threads on Java 21)
            executor.execute(() -> {
                try {
                    byte[] response = processRequest(req);
                    ctx.writeAndFlush(Unpooled.wrappedBuffer(response));
                } catch (Exception e) {
                    try {
                        byte[] err = encodeResponse(BinaryProtocol.STATUS_ERROR, null);
                        ctx.writeAndFlush(Unpooled.wrappedBuffer(err));
                    } catch (Exception ex) {
                        ctx.close();
                    }
                }
            });
        } finally {
            buf.release();
        }
    }

    private byte[] processRequest(BinaryProtocol.Request req) throws IOException {
        return switch (req.op()) {
            case BinaryProtocol.OP_PUT -> {
                storage.put(req.key(), req.value() != null ? req.value() : new byte[0]);
                yield encodeResponse(BinaryProtocol.STATUS_OK, null);
            }
            case BinaryProtocol.OP_GET -> {
                byte[] value = storage.get(req.key());
                yield encodeResponse(
                        value != null ? BinaryProtocol.STATUS_OK : BinaryProtocol.STATUS_NOT_FOUND,
                        value
                );
            }
            case BinaryProtocol.OP_DEL -> {
                storage.del(req.key());
                yield encodeResponse(BinaryProtocol.STATUS_OK, null);
            }
            default -> encodeResponse(BinaryProtocol.STATUS_ERROR, null);
        };
    }

    private byte[] encodeResponse(byte status, byte[] value) {
        ByteBuffer buf = BinaryProtocol.encodeResponse(status, value);
        byte[] arr = new byte[buf.remaining()];
        buf.get(arr);
        return arr;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
