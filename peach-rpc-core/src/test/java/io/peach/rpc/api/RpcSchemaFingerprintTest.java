package io.peach.rpc.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.util.List;
import org.junit.jupiter.api.Test;

class RpcSchemaFingerprintTest {

    @Test
    void shouldProduceStableUserTypeIds() throws Exception {
        Type type = Contract.class
                .getMethod("find", List.class)
                .getGenericParameterTypes()[0];

        int first = RpcTypeIds.typeId(type);
        int second = RpcTypeIds.typeId(type);

        assertEquals(first, second);
        assertTrue(first > RpcTypeIds.FRAMEWORK_RESERVED_MAX);
    }

    @Test
    void shouldDetectDtoSchemaDifference() {
        ServiceKey key = new ServiceKey(
                Contract.class.getName(),
                "1.0.0",
                "default");

        String first = RpcSchemaFingerprint.serviceFingerprint(
                key,
                Contract.class);
        String second = RpcSchemaFingerprint.serviceFingerprint(
                new ServiceKey(
                        ContractV2.class.getName(),
                        "1.0.0",
                        "default"),
                ContractV2.class);

        assertNotEquals(first, second);
        assertEquals(64, first.length());
    }

    @Test
    void typeRegistryShouldBeDeterministic() {
        RpcTypeRegistry registry = new RpcTypeRegistry();

        int first = registry.register(Request.class);
        int second = registry.register(Request.class);

        assertEquals(first, second);
        assertEquals(
                Request.class.getName(),
                registry.snapshot().get(first));
    }

    interface Contract {
        Response find(List<Request> request);
    }

    interface ContractV2 {
        ResponseV2 find(List<Request> request);
    }

    record Request(String value) {
    }

    record Response(String value) {
    }

    record ResponseV2(String value, long revision) {
    }
}
