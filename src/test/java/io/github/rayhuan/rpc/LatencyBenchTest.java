package io.github.rayhuan.rpc;

import io.github.rayhuan.rpc.demo.EchoService;
import io.github.rayhuan.rpc.demo.EchoServiceImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Latency probe kept as a test so it runs with the normal classpath:
 *
 * <pre>mvn -B test -Dtest=LatencyBenchTest -DfailIfNoSpecifiedTests=false</pre>
 *
 * Not part of the default suite (tagged "bench") — wall-clock numbers on a shared CI box
 * are noise, so this only asserts the call path stays under a generous ceiling.
 */
@Tag("bench")
class LatencyBenchTest {

    @Test
    void sequentialCallsStayFast() throws Exception {
        int warmup = 500;
        int total = 2000;

        ServiceRegistry registry = new ServiceRegistry().export("echo", new EchoServiceImpl());
        try (RpcServer server = new RpcServer(registry).start(0);
             RpcClient client = new RpcClient().connect("127.0.0.1", server.port()).timeout(5_000)) {

            EchoService echo = client.proxy(EchoService.class, "echo");
            for (int i = 0; i < warmup; i++) {
                echo.echo("warm");
            }

            long[] nanos = new long[total];
            long start = System.nanoTime();
            for (int i = 0; i < total; i++) {
                long t0 = System.nanoTime();
                echo.echo("payload-" + i);
                nanos[i] = System.nanoTime() - t0;
            }
            double elapsedSec = (System.nanoTime() - start) / 1e9;

            Arrays.sort(nanos);
            System.out.printf("bench: calls=%d wall=%.2fs throughput=%.0f calls/s p50=%.3fms p95=%.3fms p99=%.3fms max=%.3fms%n",
                    total, elapsedSec, total / elapsedSec,
                    ms(nanos, 0.50), ms(nanos, 0.95), ms(nanos, 0.99), nanos[total - 1] / 1e6);

            assertTrue(ms(nanos, 0.50) < 50, "p50 too slow: " + ms(nanos, 0.50) + "ms");
        }
    }

    private static double ms(long[] sorted, double q) {
        int idx = (int) Math.min(sorted.length - 1, Math.floor(q * sorted.length));
        return sorted[idx] / 1e6;
    }
}
