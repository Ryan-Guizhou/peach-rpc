package io.peach.rpc.proxy.bytebuddy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class ByteBuddyProxyFactoryTest {

    @Test
    void shouldCreateInterfaceProxyForSyncAndAsyncMethods() {
        ByteBuddyProxyFactory factory =
                new ByteBuddyProxyFactory();

        SampleService proxy = factory.create(
                SampleService.class,
                (method, arguments) -> {
                    if ("sync".equals(method.getName())) {
                        return CompletableFuture.completedFuture(
                                "sync:" + arguments[0]);
                    }
                    return CompletableFuture.completedFuture(
                            "async:" + arguments[0]);
                });

        assertEquals("sync:value", proxy.sync("value"));
        assertEquals(
                "async:value",
                proxy.async("value")
                        .toCompletableFuture()
                        .join());
    }

    interface SampleService {
        String sync(String value);

        CompletionStage<String> async(String value);
    }
}
