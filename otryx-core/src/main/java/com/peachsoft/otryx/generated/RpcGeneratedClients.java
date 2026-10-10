package io.peach.rpc.generated;

import java.util.Optional;

/** Generated Client Stub 的启动阶段发现工具。 */
public final class RpcGeneratedClients {

    /** 生成类名称后缀。 */
    public static final String FACTORY_SUFFIX = "PeachRpcClientFactory";

    private static final ClassValue<Optional<RpcGeneratedClientFactory<?>>> FACTORIES =
            new ClassValue<>() {
                @Override
                protected Optional<RpcGeneratedClientFactory<?>> computeValue(
                        Class<?> type) {
                    return load(type);
                }
            };

    private RpcGeneratedClients() {
    }

    /**
     * 尝试加载指定服务的编译期 Stub 工厂。
     *
     * @param serviceType 服务接口
     * @param <T> 服务接口类型
     * @return 找到时返回生成工厂
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<RpcGeneratedClientFactory<T>> find(
            Class<T> serviceType) {
        return (Optional<RpcGeneratedClientFactory<T>>) (Optional<?>)
                FACTORIES.get(serviceType);
    }

    private static Optional<RpcGeneratedClientFactory<?>> load(
            Class<?> serviceType) {
        String packageName = serviceType.getPackageName();
        String simpleFactoryName =
                serviceType.getSimpleName() + FACTORY_SUFFIX;
        String factoryName = packageName.isEmpty()
                ? simpleFactoryName
                : packageName + '.' + simpleFactoryName;
        try {
            Class<?> type = Class.forName(
                    factoryName,
                    true,
                    serviceType.getClassLoader());
            Object instance = type.getDeclaredConstructor().newInstance();
            if (!(instance instanceof RpcGeneratedClientFactory<?> factory)) {
                throw new IllegalStateException(
                        "Generated RPC client factory has invalid type: "
                                + factoryName);
            }
            if (factory.serviceType() != serviceType) {
                throw new IllegalStateException(
                        "Generated RPC client factory targets unexpected service: "
                                + factoryName);
            }
            return Optional.of(factory);
        } catch (ClassNotFoundException ignored) {
            return Optional.empty();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(
                    "Failed to load generated RPC client factory: "
                            + factoryName,
                    error);
        }
    }
}
