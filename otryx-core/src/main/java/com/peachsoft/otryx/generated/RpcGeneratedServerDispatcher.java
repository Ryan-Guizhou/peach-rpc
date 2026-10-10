package io.peach.rpc.generated;

/** 编译期生成的 Provider 方法分派器。 */
@FunctionalInterface
public interface RpcGeneratedServerDispatcher {

    /**
     * 根据稳定 Method ID 直接调用服务实现。
     *
     * @param methodId 方法 ID
     * @param arguments 已解码参数
     * @return 服务返回值
     * @throws Throwable 业务调用异常
     */
    Object invoke(int methodId, Object[] arguments) throws Throwable;
}
