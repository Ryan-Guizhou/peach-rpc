package com.peachsoft.otryx.proxy.jdk;

import com.peachsoft.otryx.proxy.ProxyFactory;
import com.peachsoft.otryx.proxy.RpcInvocation;
import com.peachsoft.otryx.spi.Extension;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletionStage;

/**
 * JDK 动态代理实现，作为接口类型的默认代理策略。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/23 10:51
 */
@Extension("jdk")
public final class JdkProxyFactory implements ProxyFactory {

    /**
     * 创建 JDK 动态代理工厂。
     */
    public JdkProxyFactory() {
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> type, RpcInvocation invocation) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException(
                    "JDK proxy requires interface: " + type.getName());
        }

        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, arguments) -> invoke(proxy, type, invocation, method, arguments));
    }

    private static Object invoke(
            Object proxy,
            Class<?> serviceType,
            RpcInvocation invocation,
            Method method,
            Object[] arguments) {
        if (method.getDeclaringClass() == Object.class) {
            return invokeObjectMethod(proxy, serviceType, method, arguments);
        }

        Object[] actualArguments = arguments == null ? new Object[0] : arguments;
        CompletionStage<Object> stage = invocation.invoke(method, actualArguments);
        if (CompletionStage.class.isAssignableFrom(method.getReturnType())) {
            return stage;
        }
        return stage.toCompletableFuture().join();
    }

    private static Object invokeObjectMethod(
            Object proxy, Class<?> serviceType, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "toString" -> "OtryxRpcProxy(" + serviceType.getName() + ")";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }
}
