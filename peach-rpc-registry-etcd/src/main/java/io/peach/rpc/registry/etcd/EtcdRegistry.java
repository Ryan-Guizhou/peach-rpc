package io.peach.rpc.registry.etcd;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.support.CloseableClient;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.options.WatchOption;
import io.grpc.stub.StreamObserver;
import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.registry.Registry;
import io.peach.rpc.registry.RegistryListener;
import io.peach.rpc.registry.RegistrySnapshot;
import io.peach.rpc.registry.RegistrySubscription;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Etcd Lease 与 Watch 的注册中心实现。
 *
 * <p>Etcd 只位于控制面。Consumer 的请求热路径读取 Core 中的本地服务目录，不访问 Etcd。
 */
final class EtcdRegistry implements Registry {
    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdRegistry.class);
    private static final String ROOT = "/peach-rpc/";
    private static final Duration RESUBSCRIBE_DELAY = Duration.ofSeconds(1);

    private final Client client;
    private final long leaseTtlSeconds;
    private final Object leaseMonitor = new Object();
    private volatile CompletableFuture<Long> leaseFuture;
    private volatile CloseableClient keepAliveHandle;

    EtcdRegistry(String[] endpoints, long leaseTtlSeconds) {
        this.client = Client.builder().endpoints(endpoints).build();
        this.leaseTtlSeconds = leaseTtlSeconds;
    }

    @Override
    public CompletionStage<Void> register(ServiceInstance instance) {
        return ensureLease()
                .thenCompose(leaseId -> client.getKVClient().put(
                        bytes(key(instance)),
                        bytes(serialize(instance)),
                        PutOption.builder().withLeaseId(leaseId).build()))
                .thenApply(ignored -> null);
    }

    @Override
    public CompletionStage<Void> unregister(ServiceInstance instance) {
        return client.getKVClient().delete(bytes(key(instance))).thenApply(ignored -> null);
    }

    @Override
    public CompletionStage<RegistrySnapshot> lookup(ServiceKey key) {
        return range(key);
    }

    @Override
    public RegistrySubscription subscribe(ServiceKey key, RegistryListener listener) {
        EtcdSubscription subscription = new EtcdSubscription(key, listener);
        subscription.start();
        return subscription;
    }

    private CompletionStage<RegistrySnapshot> range(ServiceKey key) {
        return client.getKVClient()
                .get(bytes(prefix(key)), GetOption.builder().isPrefix(true).build())
                .thenApply(response -> toSnapshot(key, response));
    }

    private static RegistrySnapshot toSnapshot(ServiceKey key, GetResponse response) {
        List<ServiceInstance> instances = response.getKvs().stream()
                .map(kv -> deserialize(key, kv.getValue().toString(StandardCharsets.UTF_8)))
                .toList();
        return new RegistrySnapshot(instances, response.getHeader().getRevision());
    }

    private CompletionStage<Long> ensureLease() {
        CompletableFuture<Long> current = leaseFuture;
        if (current != null) {
            return current;
        }
        synchronized (leaseMonitor) {
            if (leaseFuture != null) {
                return leaseFuture;
            }
            CompletableFuture<Long> created = new CompletableFuture<>();
            leaseFuture = created;
            client.getLeaseClient().grant(leaseTtlSeconds).whenComplete((response, error) -> {
                if (error != null) {
                    synchronized (leaseMonitor) {
                        leaseFuture = null;
                    }
                    created.completeExceptionally(error);
                    return;
                }
                long leaseId = response.getID();
                keepAliveHandle = client.getLeaseClient().keepAlive(leaseId, new StreamObserver<>() {
                    @Override
                    public void onNext(LeaseKeepAliveResponse value) {
                        // Lease health is intentionally silent on the normal path.
                    }

                    @Override
                    public void onError(Throwable error) {
                        LOGGER.error("Etcd lease keepalive failed for leaseId={}", leaseId, error);
                        invalidateLease();
                    }

                    @Override
                    public void onCompleted() {
                        LOGGER.warn("Etcd lease keepalive completed for leaseId={}", leaseId);
                        invalidateLease();
                    }
                });
                created.complete(leaseId);
            });
            return created;
        }
    }

    private void invalidateLease() {
        synchronized (leaseMonitor) {
            leaseFuture = null;
            if (keepAliveHandle != null) {
                keepAliveHandle.close();
                keepAliveHandle = null;
            }
        }
    }

    private final class EtcdSubscription implements RegistrySubscription {
        private final ServiceKey serviceKey;
        private final RegistryListener listener;
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile Watch.Watcher watcher;

        private EtcdSubscription(ServiceKey serviceKey, RegistryListener listener) {
            this.serviceKey = Objects.requireNonNull(serviceKey, "serviceKey");
            this.listener = Objects.requireNonNull(listener, "listener");
        }

        private void start() {
            if (closed.get()) {
                return;
            }
            range(serviceKey).whenComplete((snapshot, error) -> {
                if (closed.get()) {
                    return;
                }
                if (error != null) {
                    LOGGER.warn("Failed to load Etcd snapshot for {}; retrying", serviceKey.canonicalName(), error);
                    scheduleRestart();
                    return;
                }
                listener.onSnapshot(snapshot);
                openWatch(snapshot.revision() + 1);
            });
        }

        private void openWatch(long revision) {
            if (closed.get()) {
                return;
            }
            WatchOption option = WatchOption.builder()
                    .isPrefix(true)
                    .withRevision(revision)
                    .build();
            watcher = client.getWatchClient().watch(bytes(prefix(serviceKey)), option, Watch.listener(
                    response -> range(serviceKey).whenComplete((snapshot, error) -> {
                        if (closed.get()) {
                            return;
                        }
                        if (error != null) {
                            LOGGER.warn("Failed to refresh Etcd snapshot for {}", serviceKey.canonicalName(), error);
                        } else {
                            listener.onSnapshot(snapshot);
                        }
                    }),
                    error -> {
                        if (!closed.get()) {
                            LOGGER.warn("Etcd watch failed for {}; resubscribing", serviceKey.canonicalName(), error);
                            scheduleRestart();
                        }
                    }));
        }

        private void scheduleRestart() {
            closeWatcher();
            Executor executor = CompletableFuture.delayedExecutor(
                    RESUBSCRIBE_DELAY.toMillis(), TimeUnit.MILLISECONDS);
            CompletableFuture.runAsync(this::start, executor);
        }

        private void closeWatcher() {
            Watch.Watcher current = watcher;
            watcher = null;
            if (current != null) {
                current.close();
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                closeWatcher();
            }
        }
    }

    private static String prefix(ServiceKey key) {
        return ROOT
                + encode(key.serviceName()) + '/'
                + encode(key.version()) + '/'
                + encode(key.group()) + '/';
    }

    private static String key(ServiceInstance instance) {
        return prefix(instance.serviceKey()) + encode(instance.instanceId());
    }

    private static String serialize(ServiceInstance instance) {
        return escape(instance.instanceId()) + '|'
                + escape(instance.endpoint().host()) + '|'
                + instance.endpoint().port() + '|'
                + instance.weight() + '|'
                + serializeMetadata(instance.metadata());
    }

    private static ServiceInstance deserialize(ServiceKey key, String value) {
        String[] parts = value.split("\\|", -1);
        if (parts.length != 5) {
            throw new IllegalArgumentException("Malformed service instance value");
        }
        return new ServiceInstance(
                unescape(parts[0]),
                key,
                new RpcEndpoint(unescape(parts[1]), Integer.parseInt(parts[2])),
                Integer.parseInt(parts[3]),
                deserializeMetadata(parts[4]));
    }

    private static String serializeMetadata(Map<String, String> metadata) {
        return metadata.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> encode(entry.getKey()) + '=' + encode(entry.getValue()))
                .reduce((left, right) -> left + '&' + right)
                .orElse("");
    }

    private static Map<String, String> deserializeMetadata(String value) {
        if (value.isEmpty()) {
            return Map.of();
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        for (String entry : value.split("&")) {
            int separator = entry.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("Malformed service metadata");
            }
            String key = decode(entry.substring(0, separator));
            String itemValue = decode(entry.substring(separator + 1));
            metadata.put(key, itemValue);
        }
        return Map.copyOf(metadata);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        return value.replace("%", "%25").replace("|", "%7C");
    }

    private static String unescape(String value) {
        return value.replace("%7C", "|").replace("%25", "%");
    }

    private static ByteSequence bytes(String value) {
        return ByteSequence.from(value, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        invalidateLease();
        client.close();
    }
}
