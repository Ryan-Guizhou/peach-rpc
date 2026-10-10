package io.peach.rpc.registry.etcd;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.etcd.jetcd.Client;
import io.etcd.jetcd.test.EtcdClusterExtension;
import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.registry.RegistrySnapshot;
import io.peach.rpc.registry.RegistrySubscription;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Etcd 多节点故障注入测试。
 *
 * <p>该类标记为 {@code chaos}，默认 Reactor 不执行。使用
 * {@code -Petcd-chaos} 单独运行，避免 3 节点容器生命周期影响普通 PR 门禁。
 */
@Tag("chaos")
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class EtcdRegistryChaosTest {

    @RegisterExtension
    static final EtcdClusterExtension CLUSTER =
            EtcdClusterExtension.builder()
                    .withClusterName(
                            "peach-rpc-etcd-chaos-"
                                    + UUID.randomUUID())
                    .withNodes(3)
                    .withSsl(false)
                    .build();

    @Test
    void shouldContinueRegistrationAndWatchAfterLeaderTransfer()
            throws Exception {
        String namespace =
                "chaos-leader-" + UUID.randomUUID();
        ServiceKey key =
                new ServiceKey(
                        "demo.LeaderTransfer",
                        "1.0.0",
                        "default");
        ServiceInstance first =
                instance(key, "leader-a", 19110);
        ServiceInstance second =
                instance(key, "leader-b", 19111);
        AtomicReference<RegistrySnapshot> latest =
                new AtomicReference<>();

        try (EtcdRegistry registry = registry(namespace, 5);
             Client client = rawClient();
             RegistrySubscription ignored =
                     registry.subscribe(key, latest::set)) {
            registry.register(first)
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            assertTrue(await(
                    Duration.ofSeconds(15),
                    () -> contains(latest.get(), first)));

            LeaderTransfer transfer = leaderTransfer(client);
            try (Client leaderClient = Client.builder()
                    .endpoints(transfer.leaderEndpoint())
                    .build()) {
                leaderClient.getMaintenanceClient()
                        .moveLeader(transfer.transfereeId())
                        .get(10, TimeUnit.SECONDS);
            }

            assertTrue(await(
                    Duration.ofSeconds(15),
                    () -> currentLeader(client)
                            != transfer.originalLeaderId()));

            assertTrue(await(
                    Duration.ofSeconds(20),
                    () -> registerEventually(
                            registry,
                            second)));
            assertTrue(await(
                    Duration.ofSeconds(20),
                    () -> contains(latest.get(), first)
                            && contains(latest.get(), second)));
        }
    }

    private static EtcdRegistry registry(
            String namespace,
            long leaseTtlSeconds) {
        return new EtcdRegistry(
                endpoints(),
                leaseTtlSeconds,
                namespace);
    }

    private static Client rawClient() {
        return Client.builder()
                .endpoints(endpoints())
                .build();
    }

    private static String[] endpoints() {
        return CLUSTER.clientEndpoints().stream()
                .map(URI::toString)
                .toArray(String[]::new);
    }

    private static LeaderTransfer leaderTransfer(
            Client client) throws Exception {
        var endpoints = CLUSTER.clientEndpoints();
        long leaderId = currentLeader(client);
        Long transfereeId = null;
        URI leaderEndpoint = null;

        for (URI endpoint : endpoints) {
            var status = client.getMaintenanceClient()
                    .statusMember(endpoint.toString())
                    .get(10, TimeUnit.SECONDS);
            long memberId =
                    status.getHeader().getMemberId();
            if (memberId == leaderId) {
                leaderEndpoint = endpoint;
            } else if (transfereeId == null) {
                transfereeId = memberId;
            }
        }
        if (leaderEndpoint == null
                || transfereeId == null) {
            throw new IllegalStateException(
                    "Etcd leader transfer target was not found");
        }
        return new LeaderTransfer(
                leaderId,
                transfereeId,
                leaderEndpoint);
    }

    private static long currentLeader(
            Client client) throws Exception {
        URI endpoint =
                CLUSTER.clientEndpoints().getFirst();
        return client.getMaintenanceClient()
                .statusMember(endpoint.toString())
                .get(10, TimeUnit.SECONDS)
                .getLeader();
    }

    private static boolean registerEventually(
            EtcdRegistry registry,
            ServiceInstance instance) {
        try {
            registry.register(instance)
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean contains(
            RegistrySnapshot snapshot,
            ServiceInstance instance) {
        return snapshot != null
                && snapshot.instances()
                        .contains(instance);
    }

    private static boolean await(
            Duration timeout,
            CheckedBoolean condition)
            throws Exception {
        long deadline =
                System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.get()) {
                return true;
            }
            Thread.sleep(100L);
        }
        return condition.get();
    }

    private static ServiceInstance instance(
            ServiceKey key,
            String id,
            int port) {
        return new ServiceInstance(
                id,
                key,
                new RpcEndpoint("127.0.0.1", port),
                100,
                Map.of("zone", "chaos"));
    }

    private record LeaderTransfer(
            long originalLeaderId,
            long transfereeId,
            URI leaderEndpoint) {
    }

    @FunctionalInterface
    private interface CheckedBoolean {
        boolean get() throws Exception;
    }
}
