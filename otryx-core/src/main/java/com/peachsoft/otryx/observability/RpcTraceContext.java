package io.peach.rpc.observability;

import io.peach.rpc.api.RpcStatus;
import java.util.Map;

/**
 * 单次逻辑 RPC 调用的低依赖 Trace 上下文。
 */
public interface RpcTraceContext {

    /**
     * 返回需要写入 RPC metadata 的 Trace 字段。
     *
     * @return 不可变 Metadata
     */
    default Map<String, String> metadata() {
        return Map.of();
    }

    /**
     * 在当前执行线程激活 Trace Context。
     *
     * @return 可关闭作用域
     */
    default RpcMetadataScope makeCurrent() {
        return RpcMetadataScope.noop();
    }

    /**
     * 结束 Trace。
     *
     * @param status RPC 状态
     * @param error 失败原因；成功时为 null
     */
    default void end(RpcStatus status, Throwable error) {
    }

    /**
     * 返回 NOOP Trace Context。
     *
     * @return NOOP Context
     */
    static RpcTraceContext noop() {
        return NoopHolder.INSTANCE;
    }

    /** NOOP Holder。 */
    final class NoopHolder {
        private static final RpcTraceContext INSTANCE =
                new RpcTraceContext() {
                };

        private NoopHolder() {
        }
    }
}
