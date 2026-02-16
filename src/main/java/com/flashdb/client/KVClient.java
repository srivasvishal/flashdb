package com.flashdb.client;

import com.flashdb.protocol.BinaryProtocol;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Netty-based KV client for connecting to FlashDB server.
 */
public class KVClient {

    private final String host;
    private final int port;
    private Channel channel;
    private EventLoopGroup group;
    private final KVClientHandler handler = new KVClientHandler();
    private int timeoutSeconds = 30;

    public KVClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** Set timeout for operations (default 30s). Use higher for bulk loads. */
    public void setTimeoutSeconds(int seconds) {
        this.timeoutSeconds = seconds;
    }

    public void connect() throws InterruptedException {
        group = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(16 * 1024 * 1024, 0, 4, 0, 4));
                        ch.pipeline().addLast(new LengthFieldPrepender(4));
                        ch.pipeline().addLast(handler);
                    }
                });

        channel = b.connect(host, port).sync().channel();
    }

    public void put(String key, byte[] value) throws Exception {
        ByteBuffer req = BinaryProtocol.encodeRequest(BinaryProtocol.OP_PUT, key, value);
        CompletableFuture<BinaryProtocol.Response> future = handler.sendRequest(channel, req);
        BinaryProtocol.Response resp = future.get(timeoutSeconds, TimeUnit.SECONDS);
        if (resp.status() != BinaryProtocol.STATUS_OK && resp.status() != BinaryProtocol.STATUS_NOT_FOUND) {
            throw new RuntimeException("Put failed: " + resp.status());
        }
    }

    public byte[] get(String key) throws Exception {
        ByteBuffer req = BinaryProtocol.encodeRequest(BinaryProtocol.OP_GET, key, null);
        CompletableFuture<BinaryProtocol.Response> future = handler.sendRequest(channel, req);
        BinaryProtocol.Response resp = future.get(timeoutSeconds, TimeUnit.SECONDS);
        if (resp.status() == BinaryProtocol.STATUS_NOT_FOUND || resp.value() == null) {
            return null;
        }
        if (resp.status() != BinaryProtocol.STATUS_OK) {
            throw new RuntimeException("Get failed: " + resp.status());
        }
        return resp.value();
    }

    public void del(String key) throws Exception {
        ByteBuffer req = BinaryProtocol.encodeRequest(BinaryProtocol.OP_DEL, key, null);
        CompletableFuture<BinaryProtocol.Response> future = handler.sendRequest(channel, req);
        BinaryProtocol.Response resp = future.get(timeoutSeconds, TimeUnit.SECONDS);
        if (resp.status() != BinaryProtocol.STATUS_OK) {
            throw new RuntimeException("Del failed: " + resp.status());
        }
    }

    public void close() {
        if (channel != null) channel.close();
        if (group != null) group.shutdownGracefully();
    }
}
