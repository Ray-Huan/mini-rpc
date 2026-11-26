package io.github.rayhuan.rpc.demo;

/** A tiny service used by the demo and the end-to-end test. */
public interface EchoService {

    String echo(String message);

    int add(int a, int b);

    String whoami();
}
