package io.peach.rpc.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.peach.rpc.api.RpcIds;
import io.peach.rpc.generated.RpcGeneratedClients;
import io.peach.rpc.generated.RpcGeneratedInvocation;
import io.peach.rpc.generated.RpcGeneratedServers;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
    void generatedClientShouldUseAritySpecificRuntimeCompatibleMethodId()
            throws Exception {
        var factory = RpcGeneratedClients.find(GreetingService.class)
                .orElseThrow();
        AtomicInteger actualMethodId = new AtomicInteger();
        AtomicInteger invoke1Count = new AtomicInteger();
        GreetingReply expected = new GreetingReply("generated");

        GreetingService client = factory.create(new RpcGeneratedInvocation() {
            @Override
            public CompletionStage<Object> invoke0(int methodId) {
                throw new AssertionError("Unexpected arity");
            }

            @Override
            public CompletionStage<Object> invoke1(
                    int methodId,
                    Object argument0) {
                actualMethodId.set(methodId);
                invoke1Count.incrementAndGet();
                assertEquals(
                        new GreetingRequest("Peach RPC"),
                        argument0);
                return CompletableFuture.completedFuture(expected);
            }

            @Override
            public CompletionStage<Object> invoke2(
                    int methodId,
                    Object argument0,
                    Object argument1) {
                throw new AssertionError("Unexpected arity");
            }

            @Override
            public CompletionStage<Object> invoke3(
                    int methodId,
                    Object argument0,
                    Object argument1,
                    Object argument2) {
                throw new AssertionError("Unexpected arity");
            }

            @Override
            public CompletionStage<Object> invoke4(
                    int methodId,
                    Object argument0,
                    Object argument1,
                    Object argument2,
                    Object argument3) {
                throw new AssertionError("Unexpected arity");
            }

            @Override
            public CompletionStage<Object> invokeN(
                    int methodId,
                    Object[] arguments) {
                throw new AssertionError("Unexpected array fallback");
            }
        });

        GreetingReply actual =
                client.hello(new GreetingRequest("Peach RPC"));
        Method method = GreetingService.class.getMethod(
                "hello",
                GreetingRequest.class);

        assertEquals(expected, actual);
        assertEquals(1, invoke1Count.get());
        assertEquals(
                RpcIds.methodId(method),
                actualMethodId.get());
    }

    @Test
    void generatedServerShouldDispatchDirectlyByMethodId()
            throws Throwable {
        GreetingService target = new GreetingServiceImpl();
        var dispatcher = RpcGeneratedServers
                .create(GreetingService.class, target)
                .orElseThrow();
        Method method = GreetingService.class.getMethod(
                "hello",
                GreetingRequest.class);

        Object result = dispatcher.invoke(
                RpcIds.methodId(method),
                new Object[] {new GreetingRequest("Peach RPC")});

        assertEquals(
                new GreetingReply("Hello, Peach RPC!"),
                result);
    }
}
