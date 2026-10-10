package io.peach.rpc.generated;

import java.util.Optional;

/** Generated Server Dispatcher 的启动阶段发现工具。 */
public final class RpcGeneratedServers {

    /** 生成服务端工厂类名称后缀。 */
    public static final String FACTORY_SUFFIX = "PeachRpcServerFactory";

    private static final ClassValue<Optional<RpcGeneratedServerFactory<?>>> FACTORIES =
            new ClassValue<>() {
                @Override
                protected Optional<RpcGeneratedServerFactory<?>> computeValue(
                        Class<?> type) {
                    return load(type);
                }
            };

    private RpcGeneratedServers() {
    }

    /**
     * 为服务实现创建编译期 Dispatcher。
     *
     * @param serviceType 服务接口
     * @param target 服务实现
     * @return 存在生成代码时返回 Dispatcher
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Optional<RpcGeneratedServerDispatcher> create(
            Class<?> serviceType,
            Object target) {
        Optional<RpcGeneratedServerFactory<?>> factory = FACTORIES.get(serviceType);
        if (factory.isEmpty()) {
            return Optional.empty();
        }
        RpcGeneratedServerFactory raw = factory.orElseThrow();
        return Optional.of(raw.create(serviceType.cast(target)));
    }

    private static Optional<RpcGeneratedServerFactory<?>> load(
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
            if (!(instance instanceof RpcGeneratedServerFactory<?> factory)) {
                throw new IllegalStateException(
                        "Generated RPC server factory has invalid type: "
                                + factoryName);
            }
            if (factory.serviceType() != serviceType) {
                throw new IllegalStateException(
                        "Generated RPC server factory targets unexpected service: "
                                + factoryName);
            }
            return Optional.of(factory);
        } catch (ClassNotFoundException ignored) {
            return Optional.empty();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(
                    "Failed to load generated RPC server factory: "
                            + factoryName,
                    error);
        }
    }
}
