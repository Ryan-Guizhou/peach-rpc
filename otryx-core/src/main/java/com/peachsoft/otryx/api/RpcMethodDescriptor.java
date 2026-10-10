package io.peach.rpc.api;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

/**
 * 启动阶段解析并可被 Codec 预绑定的 RPC 方法描述。
 *
 * @param serviceKey 服务唯一键
 * @param serviceId 服务标识
 * @param methodId 方法标识
 * @param methodName 方法名
 * @param parameterTypes 参数泛型类型
 * @param returnType 返回泛型类型
 */
public record RpcMethodDescriptor(
        ServiceKey serviceKey,
        int serviceId,
        int methodId,
        String methodName,
        List<Type> parameterTypes,
        Type returnType) {

    /** 校验并固化描述信息。 */
    public RpcMethodDescriptor {
        Objects.requireNonNull(serviceKey, "serviceKey");
        Objects.requireNonNull(methodName, "methodName");
        parameterTypes = List.copyOf(parameterTypes);
        Objects.requireNonNull(returnType, "returnType");
    }

    /**
     * 从 Java 方法创建描述信息。
     *
     * @param serviceKey 服务唯一键
     * @param method Java 方法
     * @return RPC 方法描述
     */
    public static RpcMethodDescriptor from(ServiceKey serviceKey, Method method) {
        return new RpcMethodDescriptor(
                serviceKey,
                RpcIds.serviceId(serviceKey),
                RpcIds.methodId(method),
                method.getName(),
                List.of(method.getGenericParameterTypes()),
                method.getGenericReturnType());
    }
}
