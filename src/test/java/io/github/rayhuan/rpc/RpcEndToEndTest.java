package io.github.rayhuan.rpc;

import io.github.rayhuan.rpc.demo.EchoService;
import io.github.rayhuan.rpc.demo.EchoServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RpcEndToEndTest {

    private RpcServer server;
    private RpcClient client;
    private EchoService echo;

    @BeforeEach
    void setUp() throws Exception {
        ServiceRegistry registry = new ServiceRegistry().export("echo", new EchoServiceImpl());
        server = new RpcServer(registry).start(0);
        client = new RpcClient().connect("127.0.0.1", server.port()).timeout(2_000);
        echo = client.proxy(EchoService.class, "echo");
    }

    @AfterEach
    void tearDown() {
        client.close();
        server.close();
    }

    @Test
    void echoesAString() {
        assertEquals("echo:hello", echo.echo("hello"));
    }

    @Test
    void addsTwoInts() {
        assertEquals(42, echo.add(40, 2));
    }

    @Test
    void reportsUnknownServiceAsError() {
        EchoService missing = client.proxy(EchoService.class, "nope");
        IllegalStateException e = assertThrows(IllegalStateException.class, missing::whoami);
        assertTrue(e.getMessage().contains("unknown service"), e.getMessage());
    }

    @Test
    void survivesConcurrentCalls() throws Exception {
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            final int n = i;
            threads[i] = new Thread(() -> assertEquals("echo:" + n, echo.echo(String.valueOf(n))));
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
    }
}
