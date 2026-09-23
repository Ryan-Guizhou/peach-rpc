package io.peach.rpc.examples;

import io.peach.rpc.spring.annotation.PeachRpcReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 示例 Consumer，在应用启动后发起一次 RPC 调用。
 */
@Component
public class GreetingRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(GreetingRunner.class);

    /**
     * 创建示例 Consumer。
     */
    public GreetingRunner() {
    }

    @PeachRpcReference(version = "1.0.0")
    private GreetingService greetingService;

    @Override
    public void run(ApplicationArguments args) {
        GreetingReply reply = greetingService.hello(new GreetingRequest("Peach RPC"));
        LOGGER.info("RPC demo completed successfully: {}", reply.message());
    }
}
