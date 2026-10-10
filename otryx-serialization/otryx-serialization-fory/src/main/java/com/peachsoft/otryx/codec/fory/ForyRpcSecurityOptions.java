package com.peachsoft.otryx.codec.fory;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Fory Native 载荷的反序列化安全策略。
 *
 * <p>兼容模式维持 1.0.x 类型标识与 Wire v1 数据格式；生产模式使用严格类型白名单，
 * 不允许来自报文的任意 Java 类名被反序列化。
 *
 * @param mode 安全模式
 * @param allowedClassPatterns STRICT_ALLOWLIST 模式下允许的应用类名称或包通配模式
 * @param maxDepth 最大对象图嵌套深度
 * @param maxGraphMemoryBytes 单次反序列化对象图近似内存上限
 * @param maxPayloadBytes 单个 Fory Payload 字节数上限
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/10/8 14:52
 */
public record ForyRpcSecurityOptions(
        Mode mode,
        Set<String> allowedClassPatterns,
        int maxDepth,
        long maxGraphMemoryBytes,
        int maxPayloadBytes) {

    /** 兼容和严格白名单两种安全模式。 */
    public enum Mode {
        /** 仅用于既有可信连接的过渡模式；生产外部流量应使用严格白名单。 */
        TRUSTED_COMPATIBILITY,
        /** 不改变旧 Payload 编码的严格类型白名单模式。 */
        STRICT_ALLOWLIST
    }

    /** 创建并校验安全策略。 */
    public ForyRpcSecurityOptions {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(allowedClassPatterns, "allowedClassPatterns");
        Set<String> normalized = new LinkedHashSet<>();
        for (String rule : allowedClassPatterns) {
            if (rule == null || rule.isBlank()) {
                throw new IllegalArgumentException(
                        "allowedClassPatterns must not contain blanks");
            }
            String value = rule.trim();
            if (value.equals("*")
                    || value.equals("java.*")
                    || value.equals("javax.*")
                    || value.equals("jdk.*")
                    || value.equals("sun.*")) {
                throw new IllegalArgumentException(
                        "Broad JDK or universal class rules are forbidden: " + value);
            }
            normalized.add(value);
        }
        allowedClassPatterns = Set.copyOf(normalized);
        if (mode == Mode.STRICT_ALLOWLIST
                && allowedClassPatterns.isEmpty()) {
            throw new IllegalArgumentException(
                    "STRICT_ALLOWLIST requires explicit allowedClassPatterns");
        }
        if (maxDepth <= 0 || maxDepth > 512) {
            throw new IllegalArgumentException(
                    "maxDepth must be between 1 and 512");
        }
        if (maxGraphMemoryBytes <= 0L || maxPayloadBytes <= 0) {
            throw new IllegalArgumentException(
                    "Graph memory and payload limits must be positive");
        }
    }

    /**
     * 返回不会改变现有 Wire v1 Payload 编码的迁移兼容策略。
     *
     * @return 兼容策略
     */
    public static ForyRpcSecurityOptions trustedCompatibility() {
        return new ForyRpcSecurityOptions(
                Mode.TRUSTED_COMPATIBILITY,
                Set.of(),
                50,
                64L * 1024L * 1024L,
                16 * 1024 * 1024);
    }

    /**
     * 为应用契约类型创建生产白名单模式。
     *
     * @param classPatterns 明确允许的 Java 类型与应用包模式
     * @return 严格白名单策略
     */
    public static ForyRpcSecurityOptions strictAllowlist(
            Set<String> classPatterns) {
        return new ForyRpcSecurityOptions(
                Mode.STRICT_ALLOWLIST,
                classPatterns,
                32,
                64L * 1024L * 1024L,
                16 * 1024 * 1024);
    }
}
