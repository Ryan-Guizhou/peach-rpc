package com.peachsoft.otryx.registry.consul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peachsoft.otryx.api.RpcEndpoint;
import com.peachsoft.otryx.api.ServiceInstance;
import com.peachsoft.otryx.api.ServiceKey;
import com.peachsoft.otryx.registry.Registry;
import com.peachsoft.otryx.registry.RegistryCapability;
import com.peachsoft.otryx.registry.RegistryContractTestKit;
import com.peachsoft.otryx.registry.RegistryOptions;
import com.peachsoft.otryx.registry.http.HttpRegistryIdentity;
import com.peachsoft.otryx.spi.ExtensionLoader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * 使用本地 HTTP Agent 模拟器执行 Consul 注册/发现/续约完整契约。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/10/10 18:00
 */
class ConsulRegistryTest {

    @Test
    void registersDiscoversAndUnregistersWithSharedContract() throws Exception {
        try (FakeAgent agent = new FakeAgent();
                Registry registry = new ConsulRegistryFactory().create(options(agent))) {
            assertTrue(registry.capabilities().supports(RegistryCapability.LEASE));
            assertTrue(registry.capabilities().supports(RegistryCapability.HEALTH));
            assertFalse(registry.capabilities().supports(RegistryCapability.REVISION));
            ServiceKey key = key();
            RegistryContractTestKit.verifyRegistrationDiscoverySubscription(
                    registry, key, instance(), Duration.ofSeconds(8));
            assertEquals(1, agent.registers.get());
            assertTrue(agent.heartbeats.get() > 0);
            assertEquals("test-acl-token", agent.seenToken.get());
        }
    }

    @Test
    void shouldReregisterAfterAgentForgetsTtlCheck() throws Exception {
        try (FakeAgent agent = new FakeAgent();
                Registry registry = new ConsulRegistryFactory().create(options(agent))) {
            registry.registrar().orElseThrow().register(instance())
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            agent.service.set(null);
            agent.passing.set(false);
            await(() -> agent.registers.get() >= 2);
            assertEquals(1, registry.lookup(key())
                    .toCompletableFuture().get(5, TimeUnit.SECONDS)
                    .instances().size());
        }
    }

    @Test
    void filtersNonPassingInstancesWithoutLeakingAclToken() throws Exception {
        try (FakeAgent agent = new FakeAgent();
                Registry registry = new ConsulRegistryFactory().create(options(agent))) {
            registry.registrar().orElseThrow().register(instance())
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            agent.passing.set(false);
            assertTrue(registry.lookup(key())
                    .toCompletableFuture().get(5, TimeUnit.SECONDS).instances().isEmpty());
            assertFalse(options(agent).toString().contains("test-acl-token"));
        }
    }

    @Test
    void refusesMultipleAgentEndpointsToProtectLocalTtlLeases() {
        RegistryOptions options = new RegistryOptions(
                java.util.List.of("http://127.0.0.1:8500", "http://127.0.0.1:8501"),
                "environment-a", Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> new ConsulRegistryFactory().create(options));
    }

    @Test
    void loadsFactoryBySpi() {
        assertTrue(ExtensionLoader.getLoader(
                com.peachsoft.otryx.registry.RegistryFactory.class)
                .getExtension("consul") instanceof ConsulRegistryFactory);
    }

    private static RegistryOptions options(FakeAgent agent) {
        return new RegistryOptions(
                java.util.List.of(agent.endpoint()), "environment-a",
                Map.of("consulToken", "test-acl-token",
                        "consulTtlSeconds", "9",
                        "consulHeartbeatSeconds", "1",
                        "consulPollIntervalMillis", "200"));
    }

    private static ServiceKey key() {
        return new ServiceKey("demo.GreetingService", "1.0.0", "test");
    }

    private static ServiceInstance instance() {
        return new ServiceInstance("consul-provider", key(),
                new RpcEndpoint("127.0.0.1", 19090), 175,
                Map.of("peach.rpc.protocol.version", "1"));
    }

    private static void await(java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100L);
        }
        assertTrue(condition.getAsBoolean());
    }

    private static final class FakeAgent implements AutoCloseable {
        private final ObjectMapper mapper = new ObjectMapper();
        private final HttpServer server;
        private final AtomicReference<JsonNode> service = new AtomicReference<>();
        private final AtomicReference<String> seenToken = new AtomicReference<>();
        private final AtomicBoolean passing = new AtomicBoolean();
        private final AtomicInteger registers = new AtomicInteger();
        private final AtomicInteger heartbeats = new AtomicInteger();

        private FakeAgent() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/agent/service/register", ex -> {
                seenToken.set(ex.getRequestHeaders().getFirst("X-Consul-Token"));
                JsonNode parsed = mapper.readTree(ex.getRequestBody());
                assertEquals(HttpRegistryIdentity.serviceName("environment-a", key()),
                        parsed.path("Name").asText());
                service.set(parsed);
                passing.set(false);
                registers.incrementAndGet();
                respond(ex, 200, "{}");
            });
            server.createContext("/v1/agent/check/pass/", ex -> {
                if (service.get() == null) {
                    respond(ex, 404, "{}");
                } else {
                    passing.set(true);
                    heartbeats.incrementAndGet();
                    respond(ex, 200, "{}");
                }
            });
            server.createContext("/v1/agent/service/deregister/", ex -> {
                service.set(null);
                passing.set(false);
                respond(ex, 200, "{}");
            });
            server.createContext("/v1/health/service/", ex -> {
                JsonNode current = service.get();
                if (current == null || !passing.get()) {
                    respond(ex, 200, "[]");
                    return;
                }
                JsonNode payload = mapper.valueToTree(java.util.List.of(Map.of(
                        "Service", Map.of(
                                "ID", current.path("ID").asText(),
                                "Address", current.path("Address").asText(),
                                "Port", current.path("Port").asInt(),
                                "Meta", current.path("Meta")),
                        "Node", Map.of("Address", "127.0.0.1"),
                        "Checks", java.util.List.of(Map.of("Status", "passing")))));
                respond(ex, 200, payload.toString());
            });
            server.start();
        }

        private String endpoint() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private static void respond(HttpExchange exchange, int status, String body)
                throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
