package io.peach.rpc.examples.consumer;

import io.peach.rpc.api.RpcUnavailableException;
import io.peach.rpc.examples.api.GreetingReply;
import io.peach.rpc.examples.api.GreetingRequest;
import io.peach.rpc.examples.api.GreetingService;
import io.peach.rpc.spring.annotation.PeachRpcReference;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 示例 Consumer，在应用启动后完成一次真实 RPC 调用。 */
@Component
public class GreetingRunner implements ApplicationRunner {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GreetingRunner.class);
    private static final Duration DISCOVERY_TIMEOUT =
            Duration.ofSeconds(10);

    @PeachRpcReference(version = "1.0.0")
    private GreetingService greetingService;

    @Value("${peach.rpc.example.repeat-interval-millis:0}")
    private long repeatIntervalMillis;

    /** 创建示例 Consumer。 */
    public GreetingRunner() {
    }

    @Override
    public void run(ApplicationArguments args) {
        do {
            invokeWithDiscoveryWait();
            if (repeatIntervalMillis <= 0L) {
                return;
            }
            sleep(repeatIntervalMillis);
        } while (!Thread.currentThread().isInterrupted());
    }

    private void invokeWithDiscoveryWait() {
        long deadline =
                System.nanoTime() + DISCOVERY_TIMEOUT.toNanos();
        RpcUnavailableException lastFailure = null;
        while (System.nanoTime() < deadline) {
            try {
                GreetingReply reply = greetingService.hello(
                        new GreetingRequest("Peach RPC"));
                LOGGER.info(
                        "RPC demo completed successfully: {}",
                        reply.message());
                return;
            } catch (RuntimeException error) {
                RpcUnavailableException unavailable =
                        unavailableFailure(error);
                if (unavailable == null) {
                    throw error;
                }
                lastFailure = unavailable;
                sleep(100L);
            }
        }
        throw new IllegalStateException(
                "RPC demo could not discover a provider within "
                        + DISCOVERY_TIMEOUT,
                lastFailure);
    }

    private static RpcUnavailableException unavailableFailure(
            RuntimeException error) {
        if (error instanceof RpcUnavailableException unavailable) {
            return unavailable;
        }
        if (error instanceof CompletionException
                && error.getCause()
                        instanceof RpcUnavailableException unavailable) {
            return unavailable;
        }
        return null;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "RPC demo wait was interrupted",
                    error);
        }
    }
}
