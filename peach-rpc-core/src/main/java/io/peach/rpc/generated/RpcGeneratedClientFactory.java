package io.peach.rpc.generated;

/**
 * 编译期生成的 Consumer Stub 工厂。
 *
 * @param <T> 服务接口类型
 */
public interface RpcGeneratedClientFactory<T> {

    /**
     * 返回服务接口。
     *
     * @return 服务接口
     */
    Class<T> serviceType();

    /**
     * 创建生成式 Consumer Stub。
     *
     * @param invocation Core 调用入口
     * @return Consumer Stub
     */
    T create(RpcGeneratedInvocation invocation);
}
