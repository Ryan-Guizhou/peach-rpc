package io.peach.rpc.api;

import java.util.Objects;

/**
 * RPC 网络端点。
 *
 * @param host 主机名或 IP 地址
 * @param port TCP 端口
 */
public record RpcEndpoint(String host, int port) {

    /** 校验端点。 */
    public RpcEndpoint {
        Objects.requireNonNull(host, "host");
        if (host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
    }

    /**
     * 返回 host:port 形式的端点名称。
     *
     * @return 端点名称
     */
    public String authority() {
        return host + ":" + port;
    }
}
