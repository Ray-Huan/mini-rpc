package io.github.rayhuan.rpc;

/** Either a value or an error message — never both. */
public record RpcResponse(String requestId, Object result, String error) {

    public static RpcResponse ok(String requestId, Object value) {
        return new RpcResponse(requestId, value, null);
    }

    public static RpcResponse fail(String requestId, String message) {
        return new RpcResponse(requestId, null, message);
    }

    public boolean hasError() {
        return error != null;
    }
}
