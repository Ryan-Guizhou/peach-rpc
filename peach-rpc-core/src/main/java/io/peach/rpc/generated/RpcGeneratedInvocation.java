package io.peach.rpc.generated;

import java.util.concurrent.CompletionStage;

/** Generated Stub 到 Core 的无 Method 调用入口。 */
@FunctionalInterface
public interface RpcGeneratedInvocation {

    /**
     * 根据预计算方法 ID 发起 RPC 调用。
     *
     * @param methodId 方法 ID
     * @param arguments 参数
     * @return 异步结果
     */
    CompletionStage<Object> invoke(int methodId, Object[] arguments);
}
