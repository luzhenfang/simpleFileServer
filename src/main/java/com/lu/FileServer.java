package com.lu;

import com.lu.handler.FileServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.stream.ChunkedWriteHandler;
import com.lu.utils.Util;

/**
 * @author luckyFang
 * @date 2021/2/4 13:27
 * @file com.lu.FileServer.java
 * @desc
 */



public class FileServer {

    public static void run(int port)  {
        NioEventLoopGroup eventGroup = new NioEventLoopGroup();
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(eventGroup,workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            socketChannel.pipeline().addLast("http-decoder",new HttpRequestDecoder());
                            socketChannel.pipeline().addLast("http-aggregator",new HttpObjectAggregator(65536));
                            socketChannel.pipeline().addLast("http-encoder",new HttpResponseEncoder());
                            socketChannel.pipeline().addLast("fileServerHandler",new FileServerHandler());
                            socketChannel.pipeline().addLast("http-chunked",new ChunkedWriteHandler());

                        }
                    })
            .option(ChannelOption.SO_BACKLOG,128)
            .childOption(ChannelOption.SO_KEEPALIVE,true);
            Util.logger.info("Server....");
            Util.logger.info("http://localhost:"+String.valueOf(port)+"/");

            ChannelFuture future = serverBootstrap.bind(port).sync();
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            eventGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
