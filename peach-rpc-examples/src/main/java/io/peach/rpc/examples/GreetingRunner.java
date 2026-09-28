package io.peach.rpc.examples;

import io.peach.rpc.spring.annotation.PeachRpcReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 示例 Consumer，在应用启动后发起一次 RPC 调用。
 */
@Component
public class GreetingRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(GreetingRunner.class);
    private final CompletableFuture<GreetingReply> completion =
            new CompletableFuture<>();

    /**
     * 创建示例 Consumer。
     */
    public GreetingRunner() {
    }

    @PeachRpcReference(version = "1.0.0")
    private GreetingService greetingService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            GreetingReply reply =
                    greetingService.hello(new GreetingRequest("Peach RPC"));
            completion.complete(reply);
            LOGGER.info(
                    "RPC demo completed successfully: {}",
                    reply.message());
        } catch (RuntimeException error) {
            completion.completeExceptionally(error);
            throw error;
        }
    }

    /**
     * 等待示例 RPC 调用完成。
     *
     * <p>主要用于启动烟测，避免测试仅验证 Spring Context 而没有验证真实 RPC 链路。
     *
     * @param timeout 最大等待时间
     * @return RPC 响应
     */
    public GreetingReply awaitCompletion(Duration timeout) {
        try {
            return completion.get(
                    timeout.toMillis(),
                    TimeUnit.MILLISECONDS);
        } catch (Exception error) {
            throw new IllegalStateException(
                    "RPC demo did not complete successfully",
                    error);
        }
    }
}
