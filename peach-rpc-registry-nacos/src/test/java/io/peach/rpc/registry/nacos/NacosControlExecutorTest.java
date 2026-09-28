package io.peach.rpc.registry.nacos;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.peach.rpc.api.ServiceKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

/** Nacos 控制面执行器背压测试。 */
class NacosControlExecutorTest {

    @Test
    void queueSaturationShouldFailFast() {
        NacosControlExecutor executor =
                new NacosControlExecutor();
        ServiceKey key = new ServiceKey(
                "demo.QueueSaturation",
                "1.0.0",
                "default");
        CountDownLatch release = new CountDownLatch(1);
        List<CompletableFuture<Void>> accepted =
                new ArrayList<>();

        try {
            for (int index = 0; index < 260; index++) {
                accepted.add(executor.submit(
                        "blocking-test",
                        key,
                        () -> release.await()));
            }

            CompletableFuture<Void> rejected =
                    executor.submit(
                            "rejected-test",
                            key,
                            () -> release.await());

            assertThrows(
                    CompletionException.class,
                    rejected::join);
        } finally {
            release.countDown();
            executor.close();
        }
    }
}
