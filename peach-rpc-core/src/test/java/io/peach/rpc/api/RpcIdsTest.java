package io.peach.rpc.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class RpcIdsTest {
    interface SampleService {
        String find(long id);
        String find(String id);
    }

    @Test
    void serviceIdShouldBeStableForSameContract() {
        ServiceKey first = new ServiceKey("demo.SampleService", "1.0.0", "default");
        ServiceKey second = new ServiceKey("demo.SampleService", "1.0.0", "default");
        assertEquals(RpcIds.serviceId(first), RpcIds.serviceId(second));
    }

    @Test
    void overloadedMethodsShouldProduceDifferentIds() throws Exception {
        Method longMethod = SampleService.class.getMethod("find", long.class);
        Method stringMethod = SampleService.class.getMethod("find", String.class);
        assertNotEquals(RpcIds.methodId(longMethod), RpcIds.methodId(stringMethod));
    }
}
