package com.peachsoft.otryx.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 验证 Registry 兼容元数据的判断规则与不匹配处理。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/30 11:16
 */
class RpcCompatibilityMetadataTest {

    @Test
    void shouldAllowLegacyProviderDuringRollingUpgrade() {
        ServiceInstance instance = instance(Map.of());

        assertEquals(
                RpcCompatibilityMetadata.Compatibility.LEGACY,
                RpcCompatibilityMetadata.compatibility(
                        instance,
                        "expected"));
    }

    @Test
    void shouldRejectExplicitSchemaMismatch() {
        ServiceInstance instance = instance(Map.of(
                RpcCompatibilityMetadata.SCHEMA_FINGERPRINT,
                "remote"));

        assertEquals(
                RpcCompatibilityMetadata.Compatibility.INCOMPATIBLE,
                RpcCompatibilityMetadata.compatibility(
                        instance,
                        "expected"));
    }

    @Test
    void shouldAcceptMatchingSchema() {
        ServiceInstance instance = instance(Map.of(
                RpcCompatibilityMetadata.SCHEMA_FINGERPRINT,
                "expected"));

        assertEquals(
                RpcCompatibilityMetadata.Compatibility.COMPATIBLE,
                RpcCompatibilityMetadata.compatibility(
                        instance,
                        "expected"));
    }

    private static ServiceInstance instance(
            Map<String, String> metadata) {
        return new ServiceInstance(
                "node-1",
                new ServiceKey(
                        "demo.Service",
                        "1.0.0",
                        "default"),
                new RpcEndpoint("127.0.0.1", 19090),
                100,
                metadata);
    }
}
