package io.github.rayhuan.rpc.demo;

import io.github.rayhuan.rpc.RpcClient;
import io.github.rayhuan.rpc.RpcServer;
import io.github.rayhuan.rpc.ServiceRegistry;

import java.util.Arrays;

/**
 * Sequential latency probe: one connection, N calls, report p50 / p95 / p99.
 *
 * <p>Not a JMH benchmark — it measures "what a caller actually feels" on loopback,
 * including JSON encode/decode and one thread hop. Numbers are printed, not asserted,
 * because CI machines vary wildly.
 */
public final class Bench {

    public static void main(String[] args) throws Exception {
        int warmup = args.length > 0 ? Integer.parseInt(args[0]) : 500;
        int total = args.length > 1 ? Integer.parseInt(args[1]) : 2000;

        ServiceRegistry registry = new ServiceRegistry().export("echo", new EchoServiceImpl());
        try (RpcServer server = new RpcServer(registry).start(0);
             RpcClient client = new RpcClient().connect("127.0.0.1", server.port()).timeout(5_000)) {

            EchoService echo = client.proxy(EchoService.class, "echo");
            for (int i = 0; i < warmup; i++) {
                echo.echo("warm");
            }

            long[] nanos = new long[total];
            long startAll = System.nanoTime();
            for (int i = 0; i < total; i++) {
                long t0 = System.nanoTime();
                echo.echo("payload-" + i);
                nanos[i] = System.nanoTime() - t0;
            }
            long elapsed = System.nanoTime() - startAll;

            Arrays.sort(nanos);
            double rps = total / (elapsed / 1e9);
            System.out.printf("calls=%d  wall=%.2fs  throughput=%.0f calls/s%n",
                    total, elapsed / 1e9, rps);
            System.out.printf("p50=%.3fms  p95=%.3fms  p99=%.3fms  max=%.3fms%n",
                    ms(nanos, 0.50), ms(nanos, 0.95), ms(nanos, 0.99), nanos[total - 1] / 1e6);
        }
    }

    private static double ms(long[] sorted, double q) {
        int idx = (int) Math.min(sorted.length - 1, Math.floor(q * sorted.length));
        return sorted[idx] / 1e6;
    }
}
