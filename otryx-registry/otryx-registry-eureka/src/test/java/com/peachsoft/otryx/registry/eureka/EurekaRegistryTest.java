package com.peachsoft.otryx.registry.eureka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.peachsoft.otryx.api.RpcEndpoint;
import com.peachsoft.otryx.api.ServiceInstance;
import com.peachsoft.otryx.api.ServiceKey;
import com.peachsoft.otryx.registry.Registry;
import com.peachsoft.otryx.registry.RegistryCapability;
import com.peachsoft.otryx.registry.RegistryContractTestKit;
import com.peachsoft.otryx.registry.RegistryOptions;
import com.peachsoft.otryx.spi.ExtensionLoader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * 使用本地 Eureka REST 兼容端点验证注册、续约和健康发现。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/10/10 18:00
 */
class EurekaRegistryTest {

    @Test
    void registersRenewsDiscoversAndUnregisters() throws Exception {
        try (FakeEureka backend = new FakeEureka();
                Registry registry = new EurekaRegistryFactory().create(options(backend))) {
            assertTrue(registry.capabilities().supports(RegistryCapability.LEASE));
            assertFalse(registry.capabilities().supports(RegistryCapability.REVISION));
            RegistryContractTestKit.verifyRegistrationDiscoverySubscription(
                    registry, key(), instance(), Duration.ofSeconds(8));
            assertEquals(1, backend.registers.get());
            assertTrue(backend.authSeen.get().startsWith("Basic "));
        }
    }

    @Test
    void selfHealsUnknownLeaseAndFiltersNonUpInstance() throws Exception {
        try (FakeEureka backend = new FakeEureka();
                Registry registry = new EurekaRegistryFactory().create(options(backend))) {
            registry.registrar().orElseThrow().register(instance())
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            backend.remote.set(null);
            await(() -> backend.registers.get() > 1);
            assertEquals(1, registry.lookup(key())
                    .toCompletableFuture().get(5, TimeUnit.SECONDS)
                    .instances().size());
            backend.down.set(true);
            assertTrue(registry.lookup(key())
                    .toCompletableFuture().get(5, TimeUnit.SECONDS)
                    .instances().isEmpty());
            assertFalse(options(backend).toString().contains("test-password"));
        }
    }

    @Test
    void loadsEurekaAdapterBySpi() {
        assertTrue(ExtensionLoader.getLoader(
                com.peachsoft.otryx.registry.RegistryFactory.class)
                .getExtension("eureka") instanceof EurekaRegistryFactory);
    }

    private static RegistryOptions options(FakeEureka backend) {
        return new RegistryOptions(
                java.util.List.of(backend.endpoint()), "environment-a",
                Map.of("eurekaUsername", "test-user",
                        "eurekaPassword", "test-password",
                        "eurekaLeaseSeconds", "9",
                        "eurekaHeartbeatSeconds", "1",
                        "eurekaPollIntervalMillis", "200"));
    }

    private static ServiceKey key() {
        return new ServiceKey("demo.GreetingService", "1.0.0", "test");
    }

    private static ServiceInstance instance() {
        return new ServiceInstance("eureka-provider", key(),
                new RpcEndpoint("127.0.0.1", 19090), 155,
                Map.of("peach.rpc.schema.version", "1"));
    }

    private static void await(java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < until) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100L);
        }
        assertTrue(condition.getAsBoolean());
    }

    private static final class FakeEureka implements AutoCloseable {
        private final ObjectMapper mapper = new ObjectMapper();
        private final HttpServer server;
        private final AtomicReference<JsonNode> remote = new AtomicReference<>();
        private final AtomicReference<String> authSeen = new AtomicReference<>();
        private final AtomicInteger registers = new AtomicInteger();
        private final AtomicBoolean down = new AtomicBoolean();

        private FakeEureka() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/eureka/apps/", this::onRequest);
            server.start();
        }

        private String endpoint() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/eureka";
        }

        private void onRequest(HttpExchange ex) throws IOException {
            authSeen.set(ex.getRequestHeaders().getFirst("Authorization"));
            String credentials = "Basic " + Base64.getEncoder().encodeToString(
                    "test-user:test-password".getBytes(StandardCharsets.UTF_8));
            if (!credentials.equals(authSeen.get())) {
                respond(ex, 401, "{}");
                return;
            }
            String path = ex.getRequestURI().getPath();
            String prefix = "/eureka/apps/";
            String servicePath = path.substring(prefix.length());
            String[] parts = servicePath.split("/");
            if ("POST".equals(ex.getRequestMethod()) && parts.length == 1) {
                JsonNode info = mapper.readTree(ex.getRequestBody()).path("instance");
                assertEquals(parts[0], info.path("app").asText());
                assertTrue(info.path("leaseInfo").path("durationInSecs").asInt() > 0);
                remote.set(info);
                down.set(false);
                registers.incrementAndGet();
                respond(ex, 204, "");
                return;
            }
            if ("GET".equals(ex.getRequestMethod()) && parts.length == 1) {
                JsonNode node = remote.get();
                if (node == null) {
                    respond(ex, 404, "{}");
                    return;
                }
                if (down.get()) {
                    ObjectNode changed = node.deepCopy();
                    changed.put("status", "DOWN");
                    node = changed;
                }
                respond(ex, 200, mapper.valueToTree(Map.of(
                        "application", Map.of("instance", java.util.List.of(node)))).toString());
                return;
            }
            if ("PUT".equals(ex.getRequestMethod()) && parts.length == 2) {
                respond(ex, remote.get() == null ? 404 : 200, "{}");
                return;
            }
            if ("DELETE".equals(ex.getRequestMethod()) && parts.length == 2) {
                remote.set(null);
                respond(ex, 200, "{}");
                return;
            }
            respond(ex, 404, "{}");
        }

        private static void respond(HttpExchange ex, int status, String value)
                throws IOException {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            try (var out = ex.getResponseBody()) {
                if (bytes.length > 0) {
                    out.write(bytes);
                }
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
