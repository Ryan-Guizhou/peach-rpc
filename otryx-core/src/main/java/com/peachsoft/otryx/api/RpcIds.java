package io.peach.rpc.api;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/** 稳定 RPC 标识计算工具。 */
public final class RpcIds {

    private RpcIds() {
    }

    /**
     * 计算服务标识。
     *
     * @param key 服务契约键
     * @return 稳定服务标识
     */
    public static int serviceId(ServiceKey key) {
        return fnv1a32(key.canonicalName());
    }

    /**
     * 计算方法标识。
     *
     * <p>方法名、参数类型和返回类型共同参与计算，支持重载方法。
     *
     * @param method Java 方法
     * @return 稳定方法标识
     */
    public static int methodId(Method method) {
        StringBuilder value = new StringBuilder(method.getName()).append('(');
        for (Class<?> type : method.getParameterTypes()) {
            value.append(type.getName()).append(';');
        }
        value.append(')').append(method.getReturnType().getName());
        return fnv1a32(value.toString());
    }

    static int fnv1a32(String value) {
        int hash = 0x811c9dc5;
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= item & 0xff;
            hash *= 0x01000193;
        }
        return hash;
    }
}
