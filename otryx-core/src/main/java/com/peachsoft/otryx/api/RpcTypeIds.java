package com.peachsoft.otryx.api;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * 稳定 RPC 类型标识工具。
 *
 * <p>1..1023 保留给框架内部类型；用户契约类型使用 1024..Integer.MAX_VALUE-1。
 * 标识只依赖规范化 Java Type 名称，不依赖 Classpath 顺序或 Codec 注册顺序。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/30 11:16
 */
public final class RpcTypeIds {

    /** 框架内部类型保留区间上界。 */
    public static final int FRAMEWORK_RESERVED_MAX = 1023;
    private static final int USER_RANGE =
            Integer.MAX_VALUE - FRAMEWORK_RESERVED_MAX - 1;

    private RpcTypeIds() {
    }

    /**
     * 计算用户契约类型稳定标识。
     *
     * @param type Java Type
     * @return 稳定类型标识
     */
    public static int typeId(Type type) {
        int hash = fnv1a32(canonicalName(type));
        return FRAMEWORK_RESERVED_MAX + 1
                + Math.floorMod(hash, USER_RANGE);
    }

    /**
     * 返回稳定、与运行顺序无关的 Type 名称。
     *
     * @param type Java Type
     * @return 规范化名称
     */
    public static String canonicalName(Type type) {
        Objects.requireNonNull(type, "type");
        if (type instanceof Class<?> clazz) {
            if (clazz.isArray()) {
                return canonicalName(clazz.getComponentType()) + "[]";
            }
            return clazz.getName();
        }
        if (type instanceof ParameterizedType parameterized) {
            StringJoiner joiner = new StringJoiner(
                    ",",
                    canonicalName(parameterized.getRawType()) + "<",
                    ">");
            for (Type argument : parameterized.getActualTypeArguments()) {
                joiner.add(canonicalName(argument));
            }
            return joiner.toString();
        }
        if (type instanceof GenericArrayType array) {
            return canonicalName(array.getGenericComponentType()) + "[]";
        }
        if (type instanceof TypeVariable<?> variable) {
            return "T:" + variable.getName();
        }
        if (type instanceof WildcardType wildcard) {
            StringJoiner upper = new StringJoiner("&");
            for (Type bound : wildcard.getUpperBounds()) {
                upper.add(canonicalName(bound));
            }
            StringJoiner lower = new StringJoiner("&");
            for (Type bound : wildcard.getLowerBounds()) {
                lower.add(canonicalName(bound));
            }
            return "?extends[" + upper + "]super[" + lower + "]";
        }
        return type.getTypeName();
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
