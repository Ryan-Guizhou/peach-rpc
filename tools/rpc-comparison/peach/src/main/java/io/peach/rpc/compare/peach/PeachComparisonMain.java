package io.peach.rpc.compare.peach;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.codec.RpcCodecIds;
import io.peach.rpc.codec.RpcCodecRegistry;
import io.peach.rpc.compare.ComparisonHarness;
import io.peach.rpc.core.PeachRpcClient;
import io.peach.rpc.core.PeachRpcServer;
import io.peach.rpc.registry.Registry;
import io.peach.rpc.registry.RegistryFactory;
import io.peach.rpc.registry.RegistryListener;
import io.peach.rpc.registry.RegistryOptions;
import io.peach.rpc.registry.RegistrySnapshot;
import io.peach.rpc.registry.RegistrySubscription;
import io.peach.rpc.registry.ServiceDiscovery;
import io.peach.rpc.spi.ExtensionLoader;
import io.peach.rpc.transport.RpcTransportFactory;
import io.peach.rpc.transport.RpcTransportOptions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

/**
 * 独立 JVM 中运行的 Peach TCP+Fory Echo Provider/Consumer。
 *
 * <p>基准使用固定 Endpoint，不引入 Nacos/Etcd 控制面开销；Provider/Consumer
 * 可在不同主机启动。该入口绝不能与 Dubbo 依赖打包在同一个 JVM 中。
 */
public final class PeachComparisonMain {

    private static final String VERSION = "1.0.0";
    private static final String GROUP = "comparison";

    private PeachComparisonMain() {
    }

    /**
     * 启动固定 Endpoint Provider 或执行 Consumer。
     *
     * @param args provider port 或 client host port concurrency warmup_seconds
     *             duration_seconds payload_bytes output.json
     * @throws Exception RPC 或负载执行失败
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "provider".equals(args[0])) {
            provider(Integer.parseInt(args[1]));
            return;
        }
        if (args.length == 8 && "client".equals(args[0])) {
            client(args);
            return;
        }
        throw new IllegalArgumentException(
                "Usage: provider <port> OR client <host> <port> "
                        + "<concurrency> <warmup_seconds> <duration_seconds> "
                        + "<payload_bytes> <output.json>");
    }

    private static void provider(int port) throws Exception {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid provider port");
        }
        Registry registry = ExtensionLoader.getLoader(RegistryFactory.class)
                .getExtension("memory")
                .create(new RegistryOptions(
                        List.of(), "comparison", Map.of()));
        RpcTransportFactory transport = transportFactory();
        RpcCodecRegistry codecs = RpcCodecRegistry.fromSpi();
        try (registry;
             PeachRpcServer server = PeachRpcServer.builder()
                     .serviceRegistrar(registry.registrar().orElseThrow())
                     .transportServer(transport.createServer(options()))
                     .codecRegistry(codecs)
                     .bindEndpoint(new RpcEndpoint("0.0.0.0", port))
                     .maxConcurrent(20000)
                     .build()
                     .registerService(
                             CompareEchoService.class,
                             (CompareEchoService) input -> input,
                             VERSION, GROUP)) {
            server.start().toCompletableFuture().join();
            System.out.printf(
                    "READY framework=peach protocol=tcp serializer=fory-native port=%d%n",
                    port);
            new CountDownLatch(1).await();
        }
    }

    private static void client(String[] args) throws Exception {
        String host = args[1];
        int port = Integer.parseInt(args[2]);
        int concurrency = Integer.parseInt(args[3]);
        RpcTransportFactory transport = transportFactory();
        try (PeachRpcClient client = PeachRpcClient.builder()
                .serviceDiscovery(new DirectDiscovery(
                        new RpcEndpoint(host, port)))
                .transportClient(transport.createClient(options()))
                .codecRegistry(RpcCodecRegistry.fromSpi())
                .responseCompletionQueueCapacity(
                        Math.max(4096, concurrency * 2))
                .timeout(Duration.ofSeconds(15))
                .build()) {
            CompareEchoService service = client.refer(
                    CompareEchoService.class, VERSION, GROUP);
            ComparisonHarness.run(
                    "peach", "tcp-v1", "fory-native", args, service::echo);
        }
    }

    private static RpcTransportFactory transportFactory() {
        return ExtensionLoader.getLoader(RpcTransportFactory.class)
                .getExtension("vertx");
    }

    private static RpcTransportOptions options() {
        return new RpcTransportOptions(
                4096,
                16 * 1024 * 1024,
                16 * 1024 * 1024,
                Duration.ofSeconds(3),
                Duration.ofSeconds(3),
                Set.of(RpcCodecIds.FORY_NATIVE),
                4);
    }

    private record DirectDiscovery(RpcEndpoint endpoint)
            implements ServiceDiscovery {

        private RegistrySnapshot snapshot(ServiceKey key) {
            return new RegistrySnapshot(
                    List.of(new ServiceInstance(
                            "fixed-comparison-provider",
                            key,
                            endpoint,
                            100,
                            Map.of())),
                    1L);
        }

        @Override
        public CompletionStage<RegistrySnapshot> lookup(ServiceKey key) {
            return CompletableFuture.completedFuture(snapshot(key));
        }

        @Override
        public RegistrySubscription subscribe(
                ServiceKey key, RegistryListener listener) {
            listener.onSnapshot(snapshot(key));
            return () -> { };
        }
    }
}
