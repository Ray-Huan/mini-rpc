package io.github.rayhuan.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Netty server: decodes a frame, finds the method by name + parameter type names,
 * coerces arguments, invokes, writes the result back on the same channel.
 */
public final class RpcServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RpcServer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ServiceRegistry registry;
    private final EventLoopGroup boss = new NioEventLoopGroup(1);
    private final EventLoopGroup workers = new NioEventLoopGroup();
    private Channel channel;

    public RpcServer(ServiceRegistry registry) {
        this.registry = registry;
    }

    public RpcServer start(int port) throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap()
                .group(boss, workers)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                                JsonFrames.splitter(),
                                JsonFrames.decoder(RpcRequest.class),
                                JsonFrames.encoder(),
                                new Dispatcher());
                    }
                });
        channel = b.bind(port).sync().channel();
        log.info("rpc server listening on {}", channel.localAddress());
        return this;
    }

    public int port() {
        return ((java.net.InetSocketAddress) channel.localAddress()).getPort();
    }

    @Override
    public void close() {
        if (channel != null) {
            channel.close();
        }
        boss.shutdownGracefully();
        workers.shutdownGracefully();
    }

    private final class Dispatcher extends SimpleChannelInboundHandler<RpcRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RpcRequest req) {
            RpcResponse resp;
            try {
                Object target = registry.lookup(req.service());
                if (target == null) {
                    resp = RpcResponse.fail(req.requestId(), "unknown service: " + req.service());
                } else {
                    Method method = resolve(target.getClass(), req);
                    Object[] args = coerce(method, req);
                    Object value = method.invoke(target, args);
                    resp = RpcResponse.ok(req.requestId(), value);
                }
            } catch (InvocationTargetException e) {
                resp = RpcResponse.fail(req.requestId(), "server error: " + e.getCause());
            } catch (Exception e) {
                resp = RpcResponse.fail(req.requestId(), "server error: " + e);
            }
            ctx.writeAndFlush(resp);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("connection error: {}", cause.toString());
            ctx.close();
        }
    }

    static Method resolve(Class<?> type, RpcRequest req) throws NoSuchMethodException {
        for (Method m : type.getMethods()) {
            if (!m.getName().equals(req.method())) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            if (params.length != req.paramTypes().size()) {
                continue;
            }
            boolean same = true;
            for (int i = 0; i < params.length; i++) {
                if (!params[i].getName().equals(req.paramTypes().get(i))) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return m;
            }
        }
        throw new NoSuchMethodException(req.service() + "#" + req.method());
    }

    private static Object[] coerce(Method method, RpcRequest req) {
        Class<?>[] types = method.getParameterTypes();
        Object[] out = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Object raw = req.args().get(i);
            out[i] = raw == null ? null : MAPPER.convertValue(raw, types[i]);
        }
        return out;
    }
}
