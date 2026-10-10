package com.peachsoft.otryx.compare.otryx;

import com.peachsoft.otryx.api.RpcEndpoint;
import com.peachsoft.otryx.api.ServiceInstance;
import com.peachsoft.otryx.api.ServiceKey;
import com.peachsoft.otryx.codec.RpcCodecIds;
import com.peachsoft.otryx.codec.RpcCodecRegistry;
import com.peachsoft.otryx.compare.ComparisonHarness;
import com.peachsoft.otryx.core.OtryxRpcClient;
import com.peachsoft.otryx.core.OtryxRpcServer;
import com.peachsoft.otryx.registry.Registry;
import com.peachsoft.otryx.registry.RegistryFactory;
import com.peachsoft.otryx.registry.RegistryListener;
import com.peachsoft.otryx.registry.RegistryOptions;
import com.peachsoft.otryx.registry.RegistrySnapshot;
import com.peachsoft.otryx.registry.RegistrySubscription;
import com.peachsoft.otryx.registry.ServiceDiscovery;
import com.peachsoft.otryx.spi.ExtensionLoader;
import com.peachsoft.otryx.transport.RpcTransportFactory;
import com.peachsoft.otryx.transport.RpcTransportOptions;
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
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/10/10 14:27
 */
public final class OtryxComparisonMain {

    private static final String VERSION = "1.0.0";
    private static final String GROUP = "comparison";

    private OtryxComparisonMain() {
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
             OtryxRpcServer server = OtryxRpcServer.builder()
                     .serviceRegistrar(registry.registrar().orElseThrow())
                     .transportServer(transport.createServer(options()))
                     .codecRegistry(codecs)
                     .bindEndpoint(new RpcEndpoint("0.0.0.0", port))
                     .advertisedHost(System.getenv().getOrDefault(
                             "RPC_COMPARISON_ADVERTISED_HOST", "127.0.0.1"))
                     .maxConcurrent(20000)
                     .build()
                     .registerService(
                             CompareEchoService.class,
                             (CompareEchoService) input -> input,
                             VERSION, GROUP)) {
            server.start().toCompletableFuture().join();
            System.out.printf(
                    "READY framework=otryx protocol=tcp serializer=fory-native port=%d%n",
                    port);
            new CountDownLatch(1).await();
        }
    }

    private static void client(String[] args) throws Exception {
        String host = args[1];
        int port = Integer.parseInt(args[2]);
        int concurrency = Integer.parseInt(args[3]);
        RpcTransportFactory transport = transportFactory();
        try (OtryxRpcClient client = OtryxRpcClient.builder()
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
                    "otryx", "tcp-v1", "fory-native", args, service::echo);
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
