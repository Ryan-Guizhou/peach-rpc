package io.peach.rpc.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Metadata Propagator 组合与故障隔离测试。 */
class RpcMetadataPropagatorTest {

    @Test
    void compositeShouldPropagateAndIsolateFailures() {
        AtomicInteger extracted =
                new AtomicInteger();
        AtomicInteger closed =
                new AtomicInteger();
        RpcMetadataPropagator failing =
                new RpcMetadataPropagator() {
                    @Override
                    public void inject(
                            Map<String, String> metadata) {
                        throw new IllegalStateException(
                                "inject failure");
                    }

                    @Override
                    public RpcMetadataScope extract(
                            Map<String, String> metadata) {
                        throw new IllegalStateException(
                                "extract failure");
                    }
                };
        RpcMetadataPropagator working =
                new RpcMetadataPropagator() {
                    @Override
                    public void inject(
                            Map<String, String> metadata) {
                        metadata.put(
                                "x-test",
                                "value");
                    }

                    @Override
                    public RpcMetadataScope extract(
                            Map<String, String> metadata) {
                        if ("value".equals(
                                metadata.get("x-test"))) {
                            extracted.incrementAndGet();
                        }
                        return closed::incrementAndGet;
                    }
                };

        RpcMetadataPropagator composite =
                RpcMetadataPropagator.composite(
                        List.of(failing, working));
        java.util.LinkedHashMap<String, String> metadata =
                new java.util.LinkedHashMap<>();
        composite.inject(metadata);
        assertEquals(
                "value",
                metadata.get("x-test"));

        try (RpcMetadataScope ignored =
                     composite.extract(metadata)) {
            assertEquals(1, extracted.get());
        }
        assertEquals(1, closed.get());
    }
}
