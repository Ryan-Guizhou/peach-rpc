package io.peach.rpc.observability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Peach RPC 低依赖 Metadata 上下文传播契约。
 *
 * <p>Core 不依赖 OpenTelemetry。Adapter 可将 W3C Trace Context、Baggage
 * 或其他上下文注入现有 RPC metadata，并在 Provider 执行线程恢复。
 */
public interface RpcMetadataPropagator {

    /**
     * 返回是否启用。
     *
     * @return 是否需要进行 metadata 传播
     */
    default boolean enabled() {
        return true;
    }

    /**
     * 将当前调用上下文注入可变 Metadata。
     *
     * @param metadata 可变 Metadata
     */
    default void inject(Map<String, String> metadata) {
    }

    /**
     * 从收到的 Metadata 恢复当前上下文。
     *
     * @param metadata 请求 Metadata
     * @return 上下文作用域
     */
    default RpcMetadataScope extract(Map<String, String> metadata) {
        return RpcMetadataScope.noop();
    }

    /**
     * 返回 NOOP Propagator。
     *
     * @return NOOP Propagator
     */
    static RpcMetadataPropagator noop() {
        return NoopHolder.INSTANCE;
    }

    /**
     * 合并多个 Propagator。
     *
     * <p>单个 Adapter 的异常会被隔离，避免传播系统破坏 RPC 主链。
     *
     * @param propagators Propagator 集合
     * @return Composite Propagator
     */
    static RpcMetadataPropagator composite(
            Iterable<? extends RpcMetadataPropagator> propagators) {
        List<RpcMetadataPropagator> values = new ArrayList<>();
        for (RpcMetadataPropagator propagator : propagators) {
            if (propagator != null && propagator.enabled()) {
                values.add(propagator);
            }
        }
        if (values.isEmpty()) {
            return noop();
        }
        List<RpcMetadataPropagator> immutable = List.copyOf(values);
        return new RpcMetadataPropagator() {
            @Override
            public void inject(Map<String, String> metadata) {
                immutable.forEach(propagator -> safely(() ->
                        propagator.inject(metadata)));
            }

            @Override
            public RpcMetadataScope extract(
                    Map<String, String> metadata) {
                List<RpcMetadataScope> scopes = new ArrayList<>();
                immutable.forEach(propagator -> {
                    try {
                        scopes.add(propagator.extract(metadata));
                    } catch (RuntimeException ignored) {
                        // Context propagation must not break RPC.
                    }
                });
                return () -> {
                    for (int index = scopes.size() - 1;
                            index >= 0;
                            index--) {
                        try {
                            scopes.get(index).close();
                        } catch (RuntimeException ignored) {
                            // Closing telemetry context must stay isolated.
                        }
                    }
                };
            }
        };
    }

    private static void safely(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException ignored) {
            // Context propagation must not fail the RPC data path.
        }
    }

    /** NOOP Holder。 */
    final class NoopHolder {
        private static final RpcMetadataPropagator INSTANCE =
                new RpcMetadataPropagator() {
                    @Override
                    public boolean enabled() {
                        return false;
                    }
                };

        private NoopHolder() {
        }
    }
}
