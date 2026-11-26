package io.github.rayhuan.rpc.demo;

/** Implementation detail: the framework only needs a plain object, no interface on the server side. */
public class EchoServiceImpl implements EchoService {

    @Override
    public String echo(String message) {
        return "echo:" + message;
    }

    @Override
    public int add(int a, int b) {
        return a + b;
    }

    @Override
    public String whoami() {
        return "mini-rpc demo";
    }
}
