package io.peach.rpc.benchmarks;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.codec.RpcCodecIds;
import io.peach.rpc.codec.RpcCodecRegistry;
import io.peach.rpc.core.PeachRpcClient;
import io.peach.rpc.core.PeachRpcServer;
import io.peach.rpc.registry.Registry;
import io.peach.rpc.registry.RegistryFactory;
import io.peach.rpc.registry.RegistryOptions;
import io.peach.rpc.observability.RpcSecurityMode;
import io.peach.rpc.spi.ExtensionLoader;
import io.peach.rpc.transport.RpcTransportFactory;
import io.peach.rpc.transport.RpcTransportOptions;
import io.peach.rpc.transport.RpcTransportSecurityOptions;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Peach RPC 完整 Unary byte[] 往返性能矩阵。
 *
 * <p>Payload 与每 Endpoint 连接分片通过 Param 控制；并发通过 JMH
 * `-t` 参数控制。该基准保留完整 Generated Stub、Fory、Protocol、
 * Vert.x Transport 与 Provider Dispatcher 路径。
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(2)
@State(Scope.Benchmark)
public class EndToEndPayloadBenchmark {

    /** 业务 Payload 大小。 */
    @Param({"64", "256", "1024", "16384", "1048576"})
    public int payloadSize;

    /** 单 Endpoint 连接分片数。 */
    @Param({"1", "2", "4", "8"})
    public int connectionsPerEndpoint;

    /** Transport 安全模式；完整矩阵脚本会显式覆盖为 PLAINTEXT/TLS。 */
    @Param({"PLAINTEXT"})
    public String transportSecurity;

    private Registry registry;
    private PeachRpcServer server;
    private PeachRpcClient client;
    private PayloadBenchmarkService service;
    private byte[] payload;

    /** 创建端到端 Payload Benchmark。 */
    public EndToEndPayloadBenchmark() {
    }

    /**
     * 启动完整 Peach RPC loopback。
     *
     * @throws Exception 端口初始化失败
     */
    @Setup(Level.Trial)
    public void setup() throws Exception {
        payload = new byte[payloadSize];
        int port = freePort();
        registry = ExtensionLoader
                .getLoader(RegistryFactory.class)
                .getExtension("memory")
                .create(new RegistryOptions(
                        List.of(),
                        "benchmark",
                        Map.of()));

        RpcCodecRegistry codecs =
                RpcCodecRegistry.fromSpi();
        RpcTransportFactory transportFactory =
                ExtensionLoader
                        .getLoader(RpcTransportFactory.class)
                        .getExtension("vertx");
        RpcTransportOptions baseTransportOptions =
                new RpcTransportOptions(
                        4096,
                        16 * 1024 * 1024,
                        16 * 1024 * 1024,
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(3),
                        Set.of(RpcCodecIds.FORY_NATIVE),
                        connectionsPerEndpoint);
        RpcTransportOptions serverTransportOptions =
                baseTransportOptions.withSecurity(
                        serverSecurity());
        RpcTransportOptions clientTransportOptions =
                baseTransportOptions.withSecurity(
                        clientSecurity());

        server = PeachRpcServer.builder()
                .serviceRegistrar(
                        registry.registrar().orElseThrow())
                .transportServer(
                        transportFactory.createServer(
                                serverTransportOptions))
                .codecRegistry(codecs)
                .bindEndpoint(
                        new RpcEndpoint(
                                "127.0.0.1",
                                port))
                .maxConcurrent(16384)
                .build()
                .registerService(
                        PayloadBenchmarkService.class,
                        (PayloadBenchmarkService) value -> value,
                        "1.0.0",
                        "benchmark");
        server.start()
                .toCompletableFuture()
                .join();

        client = PeachRpcClient.builder()
                .serviceDiscovery(registry)
                .transportClient(
                        transportFactory.createClient(
                                clientTransportOptions))
                .codecRegistry(codecs)
                .timeout(Duration.ofSeconds(10))
                .build();
        service = client.refer(
                PayloadBenchmarkService.class,
                "1.0.0",
                "benchmark");

        service.echo(payload);
    }

    /**
     * 测量完整 Peach RPC byte[] echo 往返。
     *
     * @return 返回 Payload 长度
     */
    @Benchmark
    public int echo() {
        return service.echo(payload).length;
    }

    /** 关闭 Benchmark 资源。 */
    @TearDown(Level.Trial)
    public void tearDown() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.close();
        }
        if (registry != null) {
            registry.close();
        }
    }

    private RpcTransportSecurityOptions serverSecurity() {
        if ("PLAINTEXT".equalsIgnoreCase(transportSecurity)) {
            return RpcTransportSecurityOptions.PLAINTEXT;
        }
        if (!"TLS".equalsIgnoreCase(transportSecurity)) {
            throw new IllegalArgumentException(
                    "Unsupported transportSecurity: " + transportSecurity);
        }
        return new RpcTransportSecurityOptions(
                RpcSecurityMode.TLS,
                requiredEnvironment("PEACH_RPC_BENCHMARK_TLS_CERT"),
                requiredEnvironment("PEACH_RPC_BENCHMARK_TLS_KEY"),
                "",
                true,
                Duration.ofSeconds(3),
                Duration.ofHours(1),
                Duration.ofDays(1));
    }

    private RpcTransportSecurityOptions clientSecurity() {
        if ("PLAINTEXT".equalsIgnoreCase(transportSecurity)) {
            return RpcTransportSecurityOptions.PLAINTEXT;
        }
        if (!"TLS".equalsIgnoreCase(transportSecurity)) {
            throw new IllegalArgumentException(
                    "Unsupported transportSecurity: " + transportSecurity);
        }
        return new RpcTransportSecurityOptions(
                RpcSecurityMode.TLS,
                "",
                "",
                requiredEnvironment("PEACH_RPC_BENCHMARK_TLS_CA"),
                true,
                Duration.ofSeconds(3),
                Duration.ofHours(1),
                Duration.ofDays(1));
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " is required for TLS benchmark mode");
        }
        return value;
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket =
                     new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
