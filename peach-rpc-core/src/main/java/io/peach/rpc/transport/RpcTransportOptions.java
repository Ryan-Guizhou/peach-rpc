package io.peach.rpc.transport;

import io.peach.rpc.codec.RpcCodecIds;
import io.peach.rpc.protocol.RpcCompressionIds;
import io.peach.rpc.protocol.RpcConnectionCapabilities;
import io.peach.rpc.protocol.RpcFeature;
import io.peach.rpc.protocol.RpcProtocolCodec;
import java.time.Duration;
import java.util.Set;

/**
 * 传输层容量、超时与连接能力配置。
 *
 * @param maxInflightPerConnection 单连接最大并发请求数
 * @param maxFrameBytes 单个完整协议帧最大字节数
 * @param maxWriteQueueBytes 单连接写队列最大字节数
 * @param connectTimeout TCP 建连超时时间
 * @param handshakeTimeout 协议握手超时时间
 * @param codecIds 当前进程可用 Codec 线协议编号
 * @param connectionsPerEndpoint 每个服务端点的连接分片数
 */
public record RpcTransportOptions(
        int maxInflightPerConnection,
        int maxFrameBytes,
        int maxWriteQueueBytes,
        Duration connectTimeout,
        Duration handshakeTimeout,
        Set<Byte> codecIds,
        int connectionsPerEndpoint) {

    /** 默认传输配置。 */
    public static final RpcTransportOptions DEFAULT = new RpcTransportOptions(
            1024,
            16 * 1024 * 1024,
            4 * 1024 * 1024,
            Duration.ofSeconds(3),
            Duration.ofSeconds(3),
            Set.of(RpcCodecIds.FORY_NATIVE),
            1);

    /**
     * 保留 V2-A 四参数构造方式。
     *
     * @param maxInflightPerConnection 单连接最大并发请求数
     * @param maxFrameBytes 单帧最大字节数
     * @param maxWriteQueueBytes 写队列最大字节数
     * @param connectTimeout 建连超时
     */
    public RpcTransportOptions(
            int maxInflightPerConnection,
            int maxFrameBytes,
            int maxWriteQueueBytes,
            Duration connectTimeout) {
        this(
                maxInflightPerConnection,
                maxFrameBytes,
                maxWriteQueueBytes,
                connectTimeout,
                connectTimeout,
                Set.of(RpcCodecIds.FORY_NATIVE),
                1);
    }

    /**
     * 保留 V2-B Codec 能力五参数构造方式。
     *
     * @param maxInflightPerConnection 单连接最大并发请求数
     * @param maxFrameBytes 单帧最大字节数
     * @param maxWriteQueueBytes 写队列最大字节数
     * @param connectTimeout 建连超时
     * @param codecIds 可用 Codec
     */
    public RpcTransportOptions(
            int maxInflightPerConnection,
            int maxFrameBytes,
            int maxWriteQueueBytes,
            Duration connectTimeout,
            Set<Byte> codecIds) {
        this(
                maxInflightPerConnection,
                maxFrameBytes,
                maxWriteQueueBytes,
                connectTimeout,
                connectTimeout,
                codecIds,
                1);
    }

    /**
     * 保留 V2-B 连接分片六参数构造方式。
     *
     * @param maxInflightPerConnection 单连接最大并发请求数
     * @param maxFrameBytes 单帧最大字节数
     * @param maxWriteQueueBytes 写队列最大字节数
     * @param connectTimeout 建连超时
     * @param codecIds 可用 Codec
     * @param connectionsPerEndpoint 每端点连接数
     */
    public RpcTransportOptions(
            int maxInflightPerConnection,
            int maxFrameBytes,
            int maxWriteQueueBytes,
            Duration connectTimeout,
            Set<Byte> codecIds,
            int connectionsPerEndpoint) {
        this(
                maxInflightPerConnection,
                maxFrameBytes,
                maxWriteQueueBytes,
                connectTimeout,
                connectTimeout,
                codecIds,
                connectionsPerEndpoint);
    }

    /** 校验容量、超时与能力配置。 */
    public RpcTransportOptions {
        if (maxInflightPerConnection <= 0
                || maxFrameBytes <= 0
                || maxWriteQueueBytes <= 0
                || connectionsPerEndpoint <= 0) {
            throw new IllegalArgumentException(
                    "transport limits must be positive");
        }
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(handshakeTimeout, "handshakeTimeout");
        codecIds = codecIds == null ? Set.of() : Set.copyOf(codecIds);
        if (codecIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one transport codec id is required");
        }
    }

    /**
     * 创建本端连接握手能力。
     *
     * @return 连接能力
     */
    public RpcConnectionCapabilities capabilities() {
        return new RpcConnectionCapabilities(
                Set.of(RpcProtocolCodec.VERSION),
                codecIds,
                Set.of(RpcCompressionIds.NONE),
                Set.of(
                        RpcFeature.DEADLINE,
                        RpcFeature.CANCEL,
                        RpcFeature.GO_AWAY),
                maxFrameBytes);
    }

    private static void requirePositive(
            Duration value,
            String name) {
        if (value == null
                || value.isNegative()
                || value.isZero()) {
            throw new IllegalArgumentException(
                    name + " must be positive");
        }
    }
}
