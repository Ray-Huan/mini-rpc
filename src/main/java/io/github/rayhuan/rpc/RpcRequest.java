package io.github.rayhuan.rpc;

import java.util.List;

/** Wire format for one call. Kept as a record so Jackson round-trips it without setters. */
public record RpcRequest(String requestId, String service, String method,
                         List<String> paramTypes, List<Object> args) {
}
