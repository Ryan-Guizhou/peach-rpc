package io.peach.rpc.observability.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.observability.RpcTraceContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** OpenTelemetry Trace Context 传播测试。 */
class OpenTelemetryRpcTracingBridgeTest {

    @Test
    void clientAndServerShouldShareDistributedTrace() {
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
                                        TextMapPropagator.composite(
                                                W3CTraceContextPropagator
                                                        .getInstance(),
                                                W3CBaggagePropagator
                                                        .getInstance())))
                        .build();

        try {
            OpenTelemetryRpcTracingBridge bridge =
                    new OpenTelemetryRpcTracingBridge(
                            openTelemetry);
            RpcTraceContext client =
                    bridge.startClient(
                            new ServiceKey(
                                    "demo.Service",
                                    "1.0.0",
                                    "default"),
                            7);
            assertFalse(client.metadata().isEmpty());

            RpcTraceContext server =
                    bridge.startServer(
                            11,
                            7,
                            client.metadata());
            server.end(RpcStatus.OK, null);
            client.end(RpcStatus.OK, null);

            List<SpanData> spans =
                    exporter.spans();
            assertEquals(2, spans.size());
            SpanData clientSpan =
                    spans.stream()
                            .filter(span ->
                                    span.getKind()
                                            == SpanKind.CLIENT)
                            .findFirst()
                            .orElseThrow();
            SpanData serverSpan =
                    spans.stream()
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
            tracerProvider.close();
        }
    }

    @Test
    void serverWithoutRemoteContextShouldIgnoreAmbientLocalSpan() {
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

        try {
            OpenTelemetryRpcTracingBridge bridge =
                    new OpenTelemetryRpcTracingBridge(
                            openTelemetry);
            Span ambient =
                    openTelemetry.getTracer("test")
                            .spanBuilder("ambient")
                            .startSpan();
            try (Scope ignored = ambient.makeCurrent()) {
                RpcTraceContext server =
                        bridge.startServer(
                                11,
                                7,
                                Map.of());
                server.end(RpcStatus.OK, null);
            } finally {
                ambient.end();
            }

            SpanData serverSpan =
                    exporter.spans().stream()
                            .filter(span ->
                                    span.getKind()
                                            == SpanKind.SERVER)
                            .findFirst()
                            .orElseThrow();
            assertEquals(
                    SpanId.getInvalid(),
                    serverSpan.getParentSpanId());
        } finally {
            tracerProvider.close();
        }
    }

    private static final class RecordingExporter
            implements SpanExporter {

        private final List<SpanData> spans =
                new ArrayList<>();

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

        private List<SpanData> spans() {
            return List.copyOf(spans);
        }
    }
}
