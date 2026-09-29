package io.peach.rpc.registry.nacos;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.observability.RpcObserver;
import io.peach.rpc.observability.RpcRegistryOperation;
import io.peach.rpc.registry.Registry;
import io.peach.rpc.registry.RegistryCapabilities;
import io.peach.rpc.registry.RegistryCapability;
import io.peach.rpc.registry.RegistryListener;
import io.peach.rpc.registry.RegistrySnapshot;
import io.peach.rpc.registry.RegistrySubscription;
import io.peach.rpc.registry.ServiceRegistrar;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 基于 Nacos NamingService 的 Registry Adapter。 */
final class NacosRegistry implements Registry, ServiceRegistrar {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(NacosRegistry.class);
    private static final Duration CLOSE_TIMEOUT =
            Duration.ofSeconds(3);
    private static final RegistryCapabilities CAPABILITIES =
            RegistryCapabilities.of(
                    RegistryCapability.REGISTRATION,
                    RegistryCapability.SUBSCRIPTION,
                    RegistryCapability.LEASE,
                    RegistryCapability.HEALTH,
                    RegistryCapability.WEIGHT,
                    RegistryCapability.CLUSTER,
                    RegistryCapability.METADATA);

    static final RegistryCapabilities CAPABILITIES_FOR_TEST = CAPABILITIES;

    private final NamingService namingService;
    private final RpcObserver observer;
    private final String namespace;
    private final String group;
    private final String cluster;
    private final NacosControlExecutor executor =
            new NacosControlExecutor();
    private final Set<NacosRegistrySubscription> subscriptions =
            ConcurrentHashMap.newKeySet();
    private final AtomicLong revision = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();

    NacosRegistry(
            NamingService namingService,
            String namespace,
            String group,
            String cluster) {
        this(
                namingService,
                namespace,
                group,
                cluster,
                RpcObserver.noop());
    }

    NacosRegistry(
            NamingService namingService,
            String namespace,
            String group,
            String cluster,
            RpcObserver observer) {
        this.namingService = Objects.requireNonNull(
                namingService,
                "namingService");
        this.observer = observer == null
                ? RpcObserver.noop()
                : observer;
        this.namespace = Objects.requireNonNull(
                namespace,
                "namespace");
        this.group = Objects.requireNonNull(group, "group");
        this.cluster = Objects.requireNonNull(cluster, "cluster");
        LOGGER.info(
                "Nacos registry initialized: namespace={}, group={}, cluster={}",
                namespace,
                group,
                cluster);
    }

    @Override
    public RegistryCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public CompletionStage<Void> register(ServiceInstance instance) {
        requireOpen();
        Objects.requireNonNull(instance, "instance");
        Instance nacos =
                NacosInstanceMapper.toNacos(instance, cluster);
        long startedAtNanos =
                observer.enabled() ? System.nanoTime() : 0L;
        CompletionStage<Void> stage = executor.submit(
                "register",
                instance.serviceKey(),
                () -> {
                    namingService.registerInstance(
                            NacosServiceNames.serviceName(
                                    instance.serviceKey()),
                            group,
                            nacos);
                    LOGGER.info(
                            "Registered RPC service in Nacos: service={}, endpoint={}",
                            instance.serviceKey().canonicalName(),
                            instance.endpoint().authority());
                });
        observeOperation(
                stage,
                RpcRegistryOperation.REGISTER,
                startedAtNanos);
        return stage;
    }

    @Override
    public CompletionStage<Void> unregister(ServiceInstance instance) {
        if (closed.get()) {
            return CompletableFuture.completedFuture(null);
        }
        Objects.requireNonNull(instance, "instance");
        Instance nacos =
                NacosInstanceMapper.toNacos(instance, cluster);
        long startedAtNanos =
                observer.enabled() ? System.nanoTime() : 0L;
        CompletionStage<Void> stage = executor.submit(
                "unregister",
                instance.serviceKey(),
                () -> {
                    namingService.deregisterInstance(
                            NacosServiceNames.serviceName(
                                    instance.serviceKey()),
                            group,
                            nacos);
                    LOGGER.info(
                            "Unregistered RPC service from Nacos: service={}, endpoint={}",
                            instance.serviceKey().canonicalName(),
                            instance.endpoint().authority());
                });
        observeOperation(
                stage,
                RpcRegistryOperation.UNREGISTER,
                startedAtNanos);
        return stage;
    }

    @Override
    public CompletionStage<RegistrySnapshot> lookup(ServiceKey key) {
        requireOpen();
        Objects.requireNonNull(key, "key");
        long startedAtNanos =
                observer.enabled() ? System.nanoTime() : 0L;
        CompletionStage<RegistrySnapshot> stage = executor.submit(
                "lookup",
                key,
                () -> new RegistrySnapshot(
                        normalize(
                                key,
                                namingService.getAllInstances(
                                        NacosServiceNames.serviceName(key),
                                        group,
                                        List.of(cluster),
                                        false)),
                        revision.incrementAndGet()));
        observeOperation(
                stage,
                RpcRegistryOperation.LOOKUP,
                startedAtNanos);
        return stage;
    }

    @Override
    public RegistrySubscription subscribe(
            ServiceKey key,
            RegistryListener listener) {
        requireOpen();
        NacosRegistrySubscription subscription =
                new NacosRegistrySubscription(
                        namingService,
                        executor,
                        key,
                        group,
                        cluster,
                        listener,
                        revision);
        subscriptions.add(subscription);
        subscription.start();
        return () -> {
            subscriptions.remove(subscription);
            subscription.close();
        };
    }

    private <T> void observeOperation(
            CompletionStage<T> stage,
            RpcRegistryOperation operation,
            long startedAtNanos) {
        if (!observer.enabled()) {
            return;
        }
        stage.whenComplete((ignored, error) ->
                observer.onRegistryOperationCompleted(
                        "nacos",
                        operation,
                        System.nanoTime() - startedAtNanos,
                        error));
    }

    private static List<ServiceInstance> normalize(
            ServiceKey key,
            List<Instance> instances) {
        return instances.stream()
                .map(instance ->
                        NacosInstanceMapper.fromNacos(
                                key,
                                instance))
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing((ServiceInstance value) ->
                                value.endpoint().host())
                        .thenComparingInt(value ->
                                value.endpoint().port())
                        .thenComparing(ServiceInstance::instanceId))
                .distinct()
                .toList();
    }

    private void requireOpen() {
        if (closed.get()) {
            throw new IllegalStateException(
                    "Nacos registry is closed");
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture<?>[] closes =
                subscriptions.stream()
                        .map(NacosRegistrySubscription::closeAsync)
                        .toArray(CompletableFuture[]::new);
        subscriptions.clear();
        try {
            CompletableFuture.allOf(closes)
                    .orTimeout(
                            CLOSE_TIMEOUT.toMillis(),
                            TimeUnit.MILLISECONDS)
                    .join();
        } catch (RuntimeException error) {
            LOGGER.warn(
                    "Nacos subscription shutdown exceeded expected bound",
                    error);
        }
        try {
            executor.submit(
                            "shutdown",
                            namespace,
                            () -> {
                                namingService.shutDown();
                                return null;
                            })
                    .orTimeout(
                            CLOSE_TIMEOUT.toMillis(),
                            TimeUnit.MILLISECONDS)
                    .join();
        } catch (RuntimeException error) {
            LOGGER.warn(
                    "Nacos naming service shutdown failed: namespace={}",
                    namespace,
                    error);
        } finally {
            executor.close();
        }
    }
}
