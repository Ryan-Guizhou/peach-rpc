package com.peachsoft.otryx.observability;

/**
 * 从 RPC Metadata 恢复出的调用上下文作用域。
 *
 * <p>实现必须允许重复 close；NOOP 作用域不做任何操作。
 */
@FunctionalInterface
public interface RpcMetadataScope extends AutoCloseable {

    /** 关闭当前上下文作用域。 */
    @Override
    void close();

    /**
     * 返回全局 NOOP Scope。
     *
     * @return NOOP Scope
     */
    static RpcMetadataScope noop() {
        return () -> { };
    }
}
