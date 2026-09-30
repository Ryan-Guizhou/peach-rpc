package io.peach.rpc.api;

import io.peach.rpc.protocol.RpcProtocolCodec;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registry 中的 RPC Wire/Schema 兼容元数据。
 */
public final class RpcCompatibilityMetadata {

    /** Registry Metadata：Wire Protocol Version。 */
    public static final String PROTOCOL_VERSION =
            "peach.rpc.protocol.version";
    /** Registry Metadata：Schema Fingerprint 规范版本。 */
    public static final String SCHEMA_VERSION =
            "peach.rpc.schema.version";
    /** Registry Metadata：服务 Schema Fingerprint。 */
    public static final String SCHEMA_FINGERPRINT =
            "peach.rpc.schema.fingerprint";

    private RpcCompatibilityMetadata() {
    }

    /**
     * 创建 Provider 发布的兼容元数据。
     *
     * @param key 服务键
     * @param api 服务接口
     * @return Registry Metadata
     */
    public static Map<String, String> providerMetadata(
            ServiceKey key,
            Class<?> api) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(
                PROTOCOL_VERSION,
                Integer.toString(
                        Byte.toUnsignedInt(
                                RpcProtocolCodec.VERSION)));
        values.put(
                SCHEMA_VERSION,
                Integer.toString(
                        RpcSchemaFingerprint.SCHEMA_VERSION));
        values.put(
                SCHEMA_FINGERPRINT,
                RpcSchemaFingerprint.serviceFingerprint(
                        key,
                        api));
        return Map.copyOf(values);
    }

    /**
     * 判定 Provider 与本地服务契约的兼容状态。
     *
     * <p>旧 Provider 缺失 Fingerprint 时返回 LEGACY，支持 N+1 Consumer
     * 与 N Provider 的滚动升级；新 Provider 携带 Fingerprint 时严格比较。
     *
     * @param instance Registry 服务实例
     * @param expectedFingerprint 本地期望 Fingerprint
     * @return 兼容状态
     */
    public static Compatibility compatibility(
            ServiceInstance instance,
            String expectedFingerprint) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(
                expectedFingerprint,
                "expectedFingerprint");
        String remote =
                instance.metadata().get(SCHEMA_FINGERPRINT);
        if (remote == null || remote.isBlank()) {
            return Compatibility.LEGACY;
        }
        return expectedFingerprint.equals(remote)
                ? Compatibility.COMPATIBLE
                : Compatibility.INCOMPATIBLE;
    }

    /** 服务实例兼容状态。 */
    public enum Compatibility {
        /** 旧节点未发布 Fingerprint，滚动升级期间允许使用。 */
        LEGACY,
        /** 新节点 Fingerprint 与 Consumer 一致。 */
        COMPATIBLE,
        /** 新节点 Fingerprint 与 Consumer 不一致，必须隔离。 */
        INCOMPATIBLE
    }
}
