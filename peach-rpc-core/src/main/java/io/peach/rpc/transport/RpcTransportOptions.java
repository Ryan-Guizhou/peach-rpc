package io.peach.rpc.transport;

import java.time.Duration;

/**
 * 传输层容量与超时配置。
 *
 * @param maxInflightPerConnection 单连接最大并发请求数
 * @param maxFrameBytes 单个完整协议帧最大字节数
 * @param maxWriteQueueBytes 单连接写队列最大字节数
 * @param connectTimeout TCP 建连超时时间
 */
public record RpcTransportOptions(
        int maxInflightPerConnection,
        int maxFrameBytes,
        int maxWriteQueueBytes,
        Duration connectTimeout) {

    /** 默认传输配置。 */
    public static final RpcTransportOptions DEFAULT = new RpcTransportOptions(
            1024,
            16 * 1024 * 1024,
            4 * 1024 * 1024,
            Duration.ofSeconds(3));

    /** 校验容量和超时配置。 */
    public RpcTransportOptions {
        if (maxInflightPerConnection <= 0 || maxFrameBytes <= 0 || maxWriteQueueBytes <= 0) {
            throw new IllegalArgumentException("transport limits must be positive");
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("connectTimeout must be positive");
        }
    }
}
