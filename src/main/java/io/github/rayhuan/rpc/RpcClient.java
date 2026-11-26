package io.github.rayhuan.rpc;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * One connection, one pending-request table, dynamic proxies for the caller side.
 *
 * <p>Requests are correlated by UUID, so concurrent calls share the socket without waiting
 * in line — the event loop writes responses back in whatever order they finish.
 */
public final class RpcClient implements AutoCloseable {

    private final EventLoopGroup group = new NioEventLoopGroup(1);
    private final Map<String, CompletableFuture<RpcResponse>> pending = new ConcurrentHashMap<>();
    private Channel channel;
    private long timeoutMs = 3_000;

    public RpcClient connect(String host, int port) throws InterruptedException {
        Bootstrap b = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                                JsonFrames.splitter(),
                                JsonFrames.decoder(RpcResponse.class),
                                JsonFrames.encoder(),
                                new Responses());
                    }
                });
        channel = b.connect(host, port).sync().channel();
        return this;
    }

    public RpcClient timeout(long millis) {
        this.timeoutMs = millis;
        return this;
    }

    /** Creates a proxy that forwards every interface method over the wire. */
    @SuppressWarnings("unchecked")
    public <T> T proxy(Class<T> iface, String serviceName) {
        InvocationHandler h = (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "RpcProxy(" + serviceName + ")";
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    default -> null;
                };
            }
            Object[] callArgs = args == null ? new Object[0] : args;
            List<String> types = java.util.Arrays.stream(method.getParameterTypes())
                    .map(Class::getName)
                    .toList();
            String id = UUID.randomUUID().toString();
            CompletableFuture<RpcResponse> future = new CompletableFuture<>();
            pending.put(id, future);
            try {
                channel.writeAndFlush(new RpcRequest(id, serviceName, method.getName(),
                        types, List.of(callArgs)));
                RpcResponse resp = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                if (resp.hasError()) {
                    throw new IllegalStateException(resp.error());
                }
                return resp.result();
            } catch (java.util.concurrent.TimeoutException e) {
                throw new IllegalStateException("rpc timeout after " + timeoutMs + "ms: "
                        + serviceName + "#" + method.getName(), e);
            } catch (java.util.concurrent.ExecutionException e) {
                throw new IllegalStateException("rpc failed: " + e.getCause(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", e);
            } finally {
                pending.remove(id);
            }
        };
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, h);
    }

    @Override
    public void close() {
        if (channel != null) {
            channel.close();
        }
        group.shutdownGracefully();
    }

    private final class Responses extends SimpleChannelInboundHandler<RpcResponse> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RpcResponse resp) {
            CompletableFuture<RpcResponse> f = pending.remove(resp.requestId());
            if (f != null) {
                f.complete(resp);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }

    /** Exposed for tests that want to assert on the proxy's own plumbing. */
    static Object callDirect(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
