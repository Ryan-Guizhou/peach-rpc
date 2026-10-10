package com.peachsoft.otryx.api;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * RPC 服务 Schema Fingerprint 计算工具。
 *
 * <p>Fingerprint 覆盖服务键、方法签名以及用户 DTO 的可序列化结构，
 * 不依赖方法枚举顺序、反射字段返回顺序或 Codec 注册顺序。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/30 11:16
 */
public final class RpcSchemaFingerprint {

    /** 当前 Fingerprint 规范版本。 */
    public static final int SCHEMA_VERSION = 1;

    private RpcSchemaFingerprint() {
    }

    /**
     * 计算完整服务契约 SHA-256 Fingerprint。
     *
     * @param key 服务键
     * @param api RPC 接口
     * @return 64 位十六进制 SHA-256 字符串
     */
    public static String serviceFingerprint(
            ServiceKey key,
            Class<?> api) {
        List<String> methods = new ArrayList<>();
        for (var method : api.getMethods()) {
            StringBuilder value = new StringBuilder()
                    .append(method.getName())
                    .append('(');
            for (Type type : method.getGenericParameterTypes()) {
                value.append(typeSchema(
                        type,
                        new HashSet<>()))
                        .append(';');
            }
            value.append(')')
                    .append(typeSchema(
                            method.getGenericReturnType(),
                            new HashSet<>()));
            methods.add(value.toString());
        }
        methods.sort(String::compareTo);

        StringBuilder schema = new StringBuilder()
                .append("schema:")
                .append(SCHEMA_VERSION)
                .append('\n')
                .append(key.canonicalName())
                .append('\n');
        methods.forEach(method -> schema
                .append(method)
                .append('\n'));
        return sha256(schema.toString());
    }

    private static String typeSchema(
            Type type,
            Set<String> visiting) {
        if (type instanceof ParameterizedType parameterized) {
            StringBuilder value = new StringBuilder(
                    RpcTypeIds.canonicalName(
                            parameterized.getRawType()))
                    .append('<');
            for (Type argument :
                    parameterized.getActualTypeArguments()) {
                value.append(typeSchema(
                        argument,
                        visiting))
                        .append(';');
            }
            return value.append('>').toString();
        }
        if (type instanceof GenericArrayType array) {
            return typeSchema(
                    array.getGenericComponentType(),
                    visiting) + "[]";
        }
        if (type instanceof WildcardType
                || !(type instanceof Class<?> clazz)) {
            return RpcTypeIds.canonicalName(type);
        }
        if (clazz.isArray()) {
            return typeSchema(
                    clazz.getComponentType(),
                    visiting) + "[]";
        }

        String name = clazz.getName();
        if (isLeaf(clazz)) {
            return name;
        }
        if (!visiting.add(name)) {
            return name + "{#recursive}";
        }

        try {
            StringBuilder value = new StringBuilder(name)
                    .append('{');
            if (clazz.isRecord()) {
                for (RecordComponent component :
                        clazz.getRecordComponents()) {
                    value.append(component.getName())
                            .append(':')
                            .append(typeSchema(
                                    component.getGenericType(),
                                    visiting))
                            .append(';');
                }
            } else {
                List<Field> fields = new ArrayList<>();
                Class<?> current = clazz;
                while (current != null
                        && current != Object.class) {
                    for (Field field :
                            current.getDeclaredFields()) {
                        int modifiers = field.getModifiers();
                        if (!Modifier.isStatic(modifiers)
                                && !Modifier.isTransient(modifiers)
                                && !field.isSynthetic()) {
                            fields.add(field);
                        }
                    }
                    current = current.getSuperclass();
                }
                fields.sort(Comparator.comparing(
                        field -> field.getDeclaringClass().getName()
                                + '#'
                                + field.getName()));
                for (Field field : fields) {
                    value.append(
                                    field.getDeclaringClass()
                                            .getName())
                            .append('#')
                            .append(field.getName())
                            .append(':')
                            .append(typeSchema(
                                    field.getGenericType(),
                                    visiting))
                            .append(';');
                }
            }
            return value.append('}').toString();
        } finally {
            visiting.remove(name);
        }
    }

    private static boolean isLeaf(Class<?> type) {
        return type.isPrimitive()
                || type.isEnum()
                || type == String.class
                || type == Void.class
                || type.getName().startsWith("java.")
                || type.getName().startsWith("javax.")
                || type.getName().startsWith("jakarta.");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(
                            value.getBytes(
                                    StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    error);
        }
    }
}
