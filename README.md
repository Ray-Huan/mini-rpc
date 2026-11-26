# mini-rpc

A **small RPC framework** in Java 17: Netty transport, length-prefixed JSON frames, dynamic proxies on the caller side, reflection dispatch on the server side.

Written to answer one interview question honestly — *"what happens between `echo.echo("hi")` and the return value?"* — with about 400 lines instead of a framework's worth of magic.

```
client proxy ──▶ RpcRequest{id, service, method, paramTypes, args}
              ──▶ [len][json] ──▶ Netty ──▶ server dispatch ──▶ method.invoke
              ◀── [len][json] ◀── RpcResponse{id, result | error}
```

## What it does

| Piece | File | Note |
|---|---|---|
| Wire format | `JsonFrames` | 4-byte length + UTF-8 JSON; 8 MB frame cap |
| Server | `RpcServer` | matches method by name **and** parameter type names |
| Client | `RpcClient` | one socket, `UUID` correlation, `CompletableFuture` per call |
| Proxy | `RpcClient#proxy` | `Object` methods handled locally, everything else goes over the wire |
| Registry | `ServiceRegistry` | name → instance, `putIfAbsent` so double export fails loudly |

## Run the tests

```bash
mvn -B test
```

```
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The tests bind a real socket on port 0 (OS-assigned), call through a proxy, and cover:
echo round-trip, `int` argument coercion, unknown-service error propagation, 8 concurrent callers on one connection.

## Use it

```java
ServiceRegistry registry = new ServiceRegistry().export("echo", new EchoServiceImpl());
try (RpcServer server = new RpcServer(registry).start(0);
     RpcClient client = new RpcClient().connect("127.0.0.1", server.port())) {

    EchoService echo = client.proxy(EchoService.class, "echo");
    echo.echo("hello");   // => "echo:hello"
    echo.add(1, 2);       // => 3
}
```

## Performance (loopback, single thread)

Measured on a laptop (Windows 11, JDK 17.0.4, Ryzen-class CPU), 500 warm-up calls then 2000 sequential
`echo("payload-i")` calls over one connection:

```
calls=2000  wall=0.30s  throughput=6753 calls/s
p50=0.127ms  p95=0.247ms  p99=0.354ms  max=16.715ms
```

Reproduce with:

```bash
mvn -B test -Dtest=LatencyBenchTest -DfailIfNoSpecifiedTests=false
```

Read this as "one thread, one socket, small payloads" — not a throughput ceiling. The number is dominated by
JSON encode/decode plus one thread hop; a pool of callers would push the QPS up and the latency spread wider.

## Design decisions

- **JSON, not protobuf.** A slower codec, but a failure can be read with `nc`. For a learning project that trade is obviously right; for production it obviously isn't.
- **Correlation id, not call-per-connection.** Concurrent calls share one socket, responses complete out of order.
- **Type names on the wire.** Overload resolution needs them; the cost is a slightly fatter request and a strict match rule (no implicit widening).
- **Fail with a message, not a stack trace.** The client turns `RpcResponse.error` into `IllegalStateException` with context (`unknown service: x`, `rpc timeout after 3000ms: echo#add`).

## Known limits

- No reconnection / heartbeat yet — a dropped connection fails the pending futures.
- No service discovery: the caller needs host and port.
- Reflection dispatch means no compile-time check that the interface matches the implementation; a typo shows up as `NoSuchMethodException` on the server.
- No streaming or async return types.

Those are the next four items, in that order.

## License

MIT
