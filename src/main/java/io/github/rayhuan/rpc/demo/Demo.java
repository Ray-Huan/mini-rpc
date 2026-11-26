package io.github.rayhuan.rpc.demo;

import io.github.rayhuan.rpc.RpcClient;
import io.github.rayhuan.rpc.RpcServer;
import io.github.rayhuan.rpc.ServiceRegistry;

/** Starts a server, calls it through a proxy, prints the answers. */
public final class Demo {

    public static void main(String[] args) throws Exception {
        ServiceRegistry registry = new ServiceRegistry().export("echo", new EchoServiceImpl());

        try (RpcServer server = new RpcServer(registry).start(0);
             RpcClient client = new RpcClient().connect("127.0.0.1", server.port())) {

            EchoService echo = client.proxy(EchoService.class, "echo");
            System.out.println(echo.echo("hello"));
            System.out.println("1 + 2 = " + echo.add(1, 2));
            System.out.println(echo.whoami());
        }
    }
}
