package com.flashdb.client;

import com.flashdb.protocol.BinaryProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * Client-side handler for request-response correlation.
 */
public class KVClientHandler extends ChannelInboundHandlerAdapter {

    private volatile CompletableFuture<BinaryProtocol.Response> currentFuture;

    public CompletableFuture<BinaryProtocol.Response> sendRequest(Channel channel, ByteBuffer req) {
        CompletableFuture<BinaryProtocol.Response> future = new CompletableFuture<>();
        currentFuture = future;
        ByteBuf buf = channel.alloc().buffer(req.remaining());
        buf.writeBytes(req);
        channel.writeAndFlush(buf);
        return future;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        try {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            BinaryProtocol.Response resp = BinaryProtocol.decodeResponse(ByteBuffer.wrap(bytes));
            if (resp != null && currentFuture != null) {
                currentFuture.complete(resp);
                currentFuture = null;
            }
        } finally {
            buf.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        if (currentFuture != null) currentFuture.completeExceptionally(cause);
        ctx.close();
    }
}
