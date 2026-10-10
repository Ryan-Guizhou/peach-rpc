package io.peach.rpc.generated;

/**
 * 编译期生成的 Provider Dispatcher 工厂。
 *
 * @param <T> 服务接口类型
 */
public interface RpcGeneratedServerFactory<T> {

    /**
     * 返回服务接口。
     *
     * @return 服务接口
     */
    Class<T> serviceType();

    /**
     * 创建绑定目标实现的 Dispatcher。
     *
     * @param target 服务实现
     * @return 生成式 Dispatcher
     */
    RpcGeneratedServerDispatcher create(T target);
}
