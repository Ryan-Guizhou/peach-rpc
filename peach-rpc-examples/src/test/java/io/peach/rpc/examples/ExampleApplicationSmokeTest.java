package io.peach.rpc.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.peach.rpc.generated.RpcGeneratedClients;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 启动完整 Spring Boot 示例并验证真实 RPC 调用链。
 */
@SpringBootTest(
        classes = DemoApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ExampleApplicationSmokeTest {

    private static final int RPC_PORT = findAvailablePort();

    @DynamicPropertySource
    static void rpcProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "peach.rpc.server.port",
                () -> RPC_PORT);
        registry.add(
                "peach.rpc.registry.type",
                () -> "memory");
    }

    @Autowired
    private GreetingRunner greetingRunner;

    @Test
    void applicationShouldStartAndCompleteRpcRoundTrip() {
        GreetingReply reply =
                greetingRunner.awaitCompletion(Duration.ofSeconds(5));

        assertEquals(
                "Hello, Peach RPC!",
                reply.message());
        assertTrue(
                RpcGeneratedClients.find(GreetingService.class)
                        .isPresent());
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Unable to allocate RPC smoke-test port",
                    error);
        }
    }
}
