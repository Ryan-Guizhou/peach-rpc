package com.peachsoft.otryx.proxy.bytebuddy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * 验证 ByteBuddy 代理方法调用与异常传播。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/23 15:46
 */
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
