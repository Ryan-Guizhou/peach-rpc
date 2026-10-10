package com.peachsoft.otryx.observability;

import com.peachsoft.otryx.api.ServiceKey;
import java.util.Map;

/**
 * OTRYX RPC 分布式 Trace 桥接契约。
 *
 * <p>Core 不依赖 OpenTelemetry。具体 Adapter 负责创建 Consumer/Provider
 * Span，并把远端 Trace Context 编码到现有 RPC metadata。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/29 14:47
 */
public interface RpcTracingBridge {

    /**
     * 返回是否启用。
     *
     * @return 是否启用 Trace
     */
    default boolean enabled() {
        return true;
    }

    /**
     * 开始一次逻辑 Consumer RPC Trace。
     *
     * @param serviceKey 服务键
     * @param methodId 方法 ID
     * @return Trace Context
     */
    default RpcTraceContext startClient(
            ServiceKey serviceKey,
            int methodId) {
        return RpcTraceContext.noop();
    }

    /**
     * 从远端 Metadata 开始 Provider RPC Trace。
     *
     * @param serviceId 服务 ID
     * @param methodId 方法 ID
     * @param metadata 请求 Metadata
     * @return Trace Context
     */
    default RpcTraceContext startServer(
            int serviceId,
            int methodId,
            Map<String, String> metadata) {
        return RpcTraceContext.noop();
    }

    /**
     * 返回 NOOP Bridge。
     *
     * @return NOOP Bridge
     */
    static RpcTracingBridge noop() {
        return NoopHolder.INSTANCE;
    }

    /** NOOP Holder。 */
    final class NoopHolder {
        private static final RpcTracingBridge INSTANCE =
                new RpcTracingBridge() {
                    @Override
                    public boolean enabled() {
                        return false;
                    }
                };

        private NoopHolder() {
        }
    }
}
