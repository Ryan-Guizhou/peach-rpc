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
import io.peach.rpc.registry.RegistryCapabilities;
import io.peach.rpc.registry.RegistryCapability;
import io.peach.rpc.registry.RegistryListener;
import io.peach.rpc.registry.RegistrySnapshot;
import io.peach.rpc.registry.RegistrySubscription;
import io.peach.rpc.registry.ServiceRegistrar;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Etcd Lease 与 Watch 的注册中心实现。
 *
 * <p>Etcd 只位于控制面。Consumer 的请求热路径读取 Core 中的本地服务目录，不访问 Etcd。
 */
final class EtcdRegistry implements Registry, ServiceRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdRegistry.class);
    private static final String DEFAULT_ROOT = "/peach-rpc/";
    private static final long RECOVERY_BASE_DELAY_MILLIS = 200L;
    private static final long RECOVERY_MAX_DELAY_MILLIS = 30_000L;
    private static final RegistryCapabilities CAPABILITIES = RegistryCapabilities.of(
            RegistryCapability.REGISTRATION,
            RegistryCapability.SUBSCRIPTION,
            RegistryCapability.REVISION,
            RegistryCapability.LEASE,
            RegistryCapability.WEIGHT,
            RegistryCapability.METADATA);

    private final Client client;
    private final long leaseTtlSeconds;
    private final String root;
    private final Object leaseMonitor = new Object();
    private final ConcurrentMap<String, ServiceInstance> activeRegistrations =
            new ConcurrentHashMap<>();
    private final AtomicBoolean leaseRecoveryScheduled = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile CompletableFuture<Long> leaseFuture;
    private volatile CloseableClient keepAliveHandle;

    EtcdRegistry(
            String[] endpoints,
            long leaseTtlSeconds,
            String namespace) {
        this.client = Client.builder().endpoints(endpoints).build();
        this.leaseTtlSeconds = leaseTtlSeconds;
        this.root = "default".equals(namespace)
                ? DEFAULT_ROOT
                : DEFAULT_ROOT + "ns/" + encode(namespace) + '/';
    }

    @Override
    public RegistryCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public CompletionStage<Void> register(ServiceInstance instance) {
        Objects.requireNonNull(instance, "instance");
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Etcd registry is closed"));
        }
        String registrationKey = key(instance);
        return ensureLease()
                .thenCompose(leaseId -> client.getKVClient().put(
                        bytes(registrationKey),
                        bytes(serialize(instance)),
                        PutOption.builder().withLeaseId(leaseId).build()))
                .thenRun(() ->
                        activeRegistrations.put(registrationKey, instance));
    }

    @Override
    public CompletionStage<Void> unregister(ServiceInstance instance) {
        Objects.requireNonNull(instance, "instance");
        String registrationKey = key(instance);
        activeRegistrations.remove(registrationKey);
        if (closed.get()) {
            return CompletableFuture.completedFuture(null);
        }
        return client.getKVClient()
                .delete(bytes(registrationKey))
                .thenApply(ignored -> null);
    }

    @Override
    public CompletionStage<RegistrySnapshot> lookup(ServiceKey key) {
        return range(key);
    }

    @Override
    public RegistrySubscription subscribe(
            ServiceKey key,
            RegistryListener listener) {
        EtcdSubscription subscription =
                new EtcdSubscription(key, listener);
        subscription.start();
        return subscription;
    }

    /**
     * 从指定历史 Revision 建立 Watch。
     *
     * <p>该包级入口仅用于确定性验证 Etcd compaction 恢复路径：
     * 当历史 Revision 已被压缩时，Watch 会先收到 CompactedException，
     * 随后复用正常恢复逻辑执行 Range + 新 Watch。
     *
     * @param key 服务键
     * @param listener 快照监听器
     * @param revision Watch 起始 Revision，必须大于 0
     * @return 可关闭订阅
     */
    RegistrySubscription subscribeFromRevision(
            ServiceKey key,
            RegistryListener listener,
            long revision) {
        if (revision <= 0L) {
            throw new IllegalArgumentException(
                    "revision must be positive");
        }
        EtcdSubscription subscription =
                new EtcdSubscription(key, listener);
        subscription.openWatch(revision);
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
                        LOGGER.error(
                                "Etcd lease keepalive failed for leaseId={}",
                                leaseId,
                                error);
                        handleLeaseLoss();
                    }

                    @Override
                    public void onCompleted() {
                        LOGGER.warn(
                                "Etcd lease keepalive completed for leaseId={}",
                                leaseId);
                        handleLeaseLoss();
                    }
                });
                created.complete(leaseId);
            });
            return created;
        }
    }

    private void handleLeaseLoss() {
        invalidateLease();
        scheduleLeaseRecovery(0);
    }

    private void invalidateLease() {
        synchronized (leaseMonitor) {
            leaseFuture = null;
            if (keepAliveHandle != null) {
                CloseableClient current = keepAliveHandle;
                keepAliveHandle = null;
                current.close();
            }
        }
    }

    private void scheduleLeaseRecovery(int attempt) {
        if (closed.get()
                || activeRegistrations.isEmpty()
                || !leaseRecoveryScheduled.compareAndSet(false, true)) {
            return;
        }
        long delayMillis = recoveryDelayMillis(attempt);
        Executor executor = CompletableFuture.delayedExecutor(
                delayMillis,
                TimeUnit.MILLISECONDS);
        CompletableFuture.runAsync(() ->
                        recoverRegistrations(attempt),
                executor);
    }

    private void recoverRegistrations(int attempt) {
        if (closed.get() || activeRegistrations.isEmpty()) {
            leaseRecoveryScheduled.set(false);
            return;
        }

        List<ServiceInstance> snapshot =
                List.copyOf(activeRegistrations.values());
        ensureLease()
                .thenCompose(leaseId -> {
                    CompletableFuture<?>[] writes = snapshot.stream()
                            .map(instance -> client.getKVClient().put(
                                    bytes(key(instance)),
                                    bytes(serialize(instance)),
                                    PutOption.builder()
                                            .withLeaseId(leaseId)
                                            .build()))
                            .map(CompletionStage::toCompletableFuture)
                            .toArray(CompletableFuture[]::new);
                    return CompletableFuture.allOf(writes);
                })
                .whenComplete((ignored, error) -> {
                    leaseRecoveryScheduled.set(false);
                    if (closed.get()) {
                        return;
                    }
                    if (error != null) {
                        LOGGER.warn(
                                "Failed to recover Etcd registrations; retrying",
                                error);
                        invalidateLease();
                        scheduleLeaseRecovery(attempt + 1);
                    } else {
                        LOGGER.info(
                                "Recovered {} Etcd RPC registrations",
                                snapshot.size());
                    }
                });
    }

    private static long recoveryDelayMillis(int attempt) {
        int shift = Math.min(Math.max(attempt, 0), 7);
        long ceiling = Math.min(
                RECOVERY_MAX_DELAY_MILLIS,
                RECOVERY_BASE_DELAY_MILLIS << shift);
        long floor = Math.max(1L, ceiling / 2L);
        return ThreadLocalRandom.current().nextLong(
                floor,
                ceiling + 1L);
    }

    private final class EtcdSubscription implements RegistrySubscription {
        private final ServiceKey serviceKey;
        private final RegistryListener listener;
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile Watch.Watcher watcher;
        private int restartAttempt;

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
                restartAttempt = 0;
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
            int attempt = restartAttempt++;
            long delayMillis = recoveryDelayMillis(attempt);
            Executor executor = CompletableFuture.delayedExecutor(
                    delayMillis,
                    TimeUnit.MILLISECONDS);
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

    private String prefix(ServiceKey key) {
        return root
                + encode(key.serviceName()) + '/'
                + encode(key.version()) + '/'
                + encode(key.group()) + '/';
    }

    private String key(ServiceInstance instance) {
        return prefix(instance.serviceKey())
                + encode(instance.instanceId());
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
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        activeRegistrations.clear();
        invalidateLease();
        client.close();
    }
}
