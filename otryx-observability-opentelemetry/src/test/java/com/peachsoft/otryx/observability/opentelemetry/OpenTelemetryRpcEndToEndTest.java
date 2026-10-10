package com.peachsoft.otryx.observability.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import com.peachsoft.otryx.api.RpcEndpoint;
import com.peachsoft.otryx.codec.RpcCodecRegistry;
import com.peachsoft.otryx.core.OtryxRpcClient;
import com.peachsoft.otryx.core.OtryxRpcServer;
import com.peachsoft.otryx.registry.Registry;
import com.peachsoft.otryx.registry.RegistryOptions;
import com.peachsoft.otryx.registry.memory.MemoryRegistryFactory;
import com.peachsoft.otryx.spi.ExtensionLoader;
import com.peachsoft.otryx.transport.RpcTransportFactory;
import com.peachsoft.otryx.transport.RpcTransportOptions;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/** OpenTelemetry 真实 RPC Trace 穿透测试。 */
class OpenTelemetryRpcEndToEndTest {

    @Test
    void realRpcShouldPreserveClientServerTraceParent() {
        RecordingExporter exporter =
                new RecordingExporter();
        SdkTracerProvider tracerProvider =
                SdkTracerProvider.builder()
                        .addSpanProcessor(
                                SimpleSpanProcessor.create(
                                        exporter))
                        .build();
        OpenTelemetrySdk openTelemetry =
                OpenTelemetrySdk.builder()
                        .setTracerProvider(tracerProvider)
                        .setPropagators(
                                ContextPropagators.create(
                                        W3CTraceContextPropagator
                                                .getInstance()))
                        .build();

        Registry registry =
                new MemoryRegistryFactory().create(
                        new RegistryOptions(
                                List.of(),
                                "",
                                java.util.Map.of()));
        RpcCodecRegistry codecs =
                RpcCodecRegistry.fromSpi();
        RpcTransportFactory transportFactory =
                ExtensionLoader.getLoader(
                                RpcTransportFactory.class)
                        .getDefaultExtension();
        OpenTelemetryRpcTracingBridge bridge =
                new OpenTelemetryRpcTracingBridge(
                        openTelemetry);

        OtryxRpcServer server = OtryxRpcServer.builder()
                .serviceRegistrar(
                        registry.registrar().orElseThrow())
                .transportServer(
                        transportFactory.createServer(
                                RpcTransportOptions.DEFAULT))
                .codecRegistry(codecs)
                .bindEndpoint(
                        new RpcEndpoint("127.0.0.1", 0))
                .advertisedHost("127.0.0.1")
                .tracingBridge(bridge)
                .build()
                .registerService(
                        GreetingService.class,
                        new GreetingServiceImpl(),
                        "1.0.0",
                        "default");
        OtryxRpcClient client = null;
        try {
            server.start().toCompletableFuture().join();

            client = OtryxRpcClient.builder()
                    .serviceDiscovery(registry)
                    .transportClient(
                            transportFactory.createClient(
                                    RpcTransportOptions.DEFAULT))
                    .codecRegistry(codecs)
                    .tracingBridge(bridge)
                    .build();

            GreetingService service = client.refer(
                    GreetingService.class,
                    "1.0.0",
                    "default");
            assertEquals(
                    "hello peach",
                    service.hello("peach"));

            List<SpanData> spans = exporter.awaitSpans(
                    2,
                    Duration.ofSeconds(2));
            SpanData clientSpan = spans.stream()
                    .filter(span ->
                            span.getKind()
                                    == SpanKind.CLIENT)
                    .findFirst()
                    .orElseThrow();
            SpanData serverSpan = spans.stream()
                    .filter(span ->
                            span.getKind()
                                    == SpanKind.SERVER)
                    .findFirst()
                    .orElseThrow();

            assertEquals(
                    clientSpan.getTraceId(),
                    serverSpan.getTraceId());
            assertEquals(
                    clientSpan.getSpanId(),
                    serverSpan.getParentSpanId());
        } finally {
            if (client != null) {
                client.close();
            }
            server.close();
            registry.close();
            tracerProvider.close();
        }
    }

    /** 测试服务。 */
    public interface GreetingService {
        /**
         * 返回问候语。
         *
         * @param name 名称
         * @return 问候语
         */
        String hello(String name);
    }

    /** 测试服务实现。 */
    public static final class GreetingServiceImpl
            implements GreetingService {
        @Override
        public String hello(String name) {
            return "hello " + name;
        }
    }

    /** 记录导出 Span 的测试 Exporter。 */
    private static final class RecordingExporter
            implements SpanExporter {

        private final List<SpanData> spans =
                new CopyOnWriteArrayList<>();

        @Override
        public CompletableResultCode export(
                Collection<SpanData> values) {
            spans.addAll(values);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

        private List<SpanData> awaitSpans(
                int expected,
                Duration timeout) {
            long deadline =
                    System.nanoTime() + timeout.toNanos();
            while (spans.size() < expected
                    && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(
                            "Interrupted while waiting for spans",
                            error);
                }
            }
            return List.copyOf(spans);
        }
    }
}
