package com.lld.im.tcp.server;

import com.lld.im.codec.WebSocketMessageDecoder;
import com.lld.im.codec.WebSocketMessageEncoder;
import com.lld.im.codec.config.BootstrapConfig;
import com.lld.im.tcp.handler.NettyServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LimWebSocketServer {

    private final static Logger logger = LoggerFactory.getLogger(LimServer.class);
    BootstrapConfig.TcpConfig config;
    EventLoopGroup mainGroup;
    EventLoopGroup subGroup;
    ServerBootstrap server;

    public LimWebSocketServer(BootstrapConfig.TcpConfig config){
        this.config = config;
        mainGroup = new NioEventLoopGroup();
        subGroup = new NioEventLoopGroup();
        server = new ServerBootstrap();
        server.group(mainGroup,subGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG,10240) //服务端可连接队列大小
                .option(ChannelOption.SO_REUSEADDR, true) //参数表示允许重复使用本地地址和端口
                .childOption(ChannelOption.TCP_NODELAY,true) //是否禁用Nagle算法 简单点说就是是否批量发送数据 true关闭 false开启。开启的话可以减少一定的网络开销，但影响消息的实时性
                .childOption(ChannelOption.SO_KEEPALIVE,true) //保活开关2h没有数据服务端会发送心跳包
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        //websocket基于http协议，所以要有http解码器
                        ChannelPipeline pipeline = socketChannel.pipeline();
                        pipeline.addLast("http-codec",new HttpServerCodec());
                        //对写大数据流的支持
                        pipeline.addLast("http-chunked",new ChunkedWriteHandler());
                        //几乎在所有的netty编程中，都会用到此handler
                        pipeline.addLast("aggregator",new HttpObjectAggregator(1024 * 1024));
                        /**
                         * websocket服务器处理的协议，用于指定给客户端连接访问的路由：/ws
                         * 本handler会帮你处理一些繁重的复杂的事
                         * 会帮你处理握手动作：handshaking（close，ping，pong） ping + pong
                         * 对于websocket来讲，都是以frames进行传输的，不同的数据类型对应的frame不同的数据类型对应的 frameopcode 也不同，通过 opcode 可以区分不同帧类型，从而实现对不同消息的解析与处理。
                         */
                        pipeline.addLast(new WebSocketServerProtocolHandler("/ws"));
                        pipeline.addLast(new WebSocketMessageDecoder());
                        pipeline.addLast(new WebSocketMessageEncoder());
                        pipeline.addLast(new NettyServerHandler(config.getBrokerId(),config.getLogicUrl()));
                    }
                });
    }

    public void start(){
        this.server.bind(config.getWebSocketPort());
        logger.info("web start");
    }

}
