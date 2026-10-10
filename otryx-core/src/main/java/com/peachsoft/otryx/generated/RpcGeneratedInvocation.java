package io.peach.rpc.generated;

import java.util.concurrent.CompletionStage;

/**
 * Generated Stub 到 Core 的按参数个数分派调用入口。
 *
 * <p>0~4 参数使用专用入口，避免 Generated Stub 为常见方法立即创建 Object[]。
 * 更高参数个数使用 invokeN 作为兼容 fallback。
 */
public interface RpcGeneratedInvocation {

    /**
     * 发起零参数 RPC 调用。
     *
     * @param methodId 方法 ID
     * @return 异步结果
     */
    CompletionStage<Object> invoke0(int methodId);

    /**
     * 发起单参数 RPC 调用。
     *
     * @param methodId 方法 ID
     * @param argument0 参数 0
     * @return 异步结果
     */
    CompletionStage<Object> invoke1(int methodId, Object argument0);

    /**
     * 发起双参数 RPC 调用。
     *
     * @param methodId 方法 ID
     * @param argument0 参数 0
     * @param argument1 参数 1
     * @return 异步结果
     */
    CompletionStage<Object> invoke2(
            int methodId,
            Object argument0,
            Object argument1);

    /**
     * 发起三参数 RPC 调用。
     *
     * @param methodId 方法 ID
     * @param argument0 参数 0
     * @param argument1 参数 1
     * @param argument2 参数 2
     * @return 异步结果
     */
    CompletionStage<Object> invoke3(
            int methodId,
            Object argument0,
            Object argument1,
            Object argument2);

    /**
     * 发起四参数 RPC 调用。
     *
     * @param methodId 方法 ID
     * @param argument0 参数 0
     * @param argument1 参数 1
     * @param argument2 参数 2
     * @param argument3 参数 3
     * @return 异步结果
     */
    CompletionStage<Object> invoke4(
            int methodId,
            Object argument0,
            Object argument1,
            Object argument2,
            Object argument3);

    /**
     * 发起多参数 RPC 调用。
     *
     * @param methodId 方法 ID
     * @param arguments 参数数组
     * @return 异步结果
     */
    CompletionStage<Object> invokeN(int methodId, Object[] arguments);
}
