package io.peach.rpc.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.peach.rpc.api.RpcIds;
import io.peach.rpc.generated.RpcGeneratedClients;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GeneratedStubTest {

    @Test
    void shouldGenerateClientFactoryForRpcContract() {
        var generated = RpcGeneratedClients.find(GreetingService.class);

        assertTrue(generated.isPresent());
        assertEquals(
                GreetingService.class,
                generated.orElseThrow().serviceType());
    }

    @Test
    void generatedClientShouldUseRuntimeCompatibleMethodId() throws Exception {
        var factory = RpcGeneratedClients.find(GreetingService.class)
                .orElseThrow();
        AtomicInteger actualMethodId = new AtomicInteger();
        GreetingReply expected = new GreetingReply("generated");

        GreetingService client = factory.create((methodId, arguments) -> {
            actualMethodId.set(methodId);
            assertEquals(1, arguments.length);
            assertEquals(new GreetingRequest("Peach RPC"), arguments[0]);
            return CompletableFuture.completedFuture(expected);
        });

        GreetingReply actual = client.hello(new GreetingRequest("Peach RPC"));
        Method method = GreetingService.class.getMethod(
                "hello",
                GreetingRequest.class);

        assertEquals(expected, actual);
        assertEquals(RpcIds.methodId(method), actualMethodId.get());
    }
}
