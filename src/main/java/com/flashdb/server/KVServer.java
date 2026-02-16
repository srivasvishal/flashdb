package com.flashdb.server;

import com.flashdb.storage.StorageEngine;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * Netty-based KV server. Handles thousands of connections with non-blocking I/O.
 */
public class KVServer {

    private final int port;
    private final Path dataDir;
    private final StorageEngine storage;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public KVServer(int port, Path dataDir) throws Exception {
        this.port = port;
        this.dataDir = dataDir;
        this.storage = new StorageEngine(dataDir);
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        // Use Virtual Threads for DB operations (Java 21)
        var executor = Executors.newVirtualThreadPerTaskExecutor();

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // Frame: 4-byte length prefix + payload (max 16MB)
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(16 * 1024 * 1024, 0, 4, 0, 4));
                        ch.pipeline().addLast(new LengthFieldPrepender(4));
                        ch.pipeline().addLast(new KVServerHandler(storage, executor));
                    }
                });

        b.bind(port).sync();
        System.out.println("FlashDB server listening on port " + port);
    }

    public void stop() {
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        try {
            storage.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public StorageEngine getStorage() {
        return storage;
    }
}
