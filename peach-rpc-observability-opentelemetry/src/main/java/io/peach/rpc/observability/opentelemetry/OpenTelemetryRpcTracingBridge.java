package io.peach.rpc.observability.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.observability.RpcMetadataScope;
import io.peach.rpc.observability.RpcTraceContext;
import io.peach.rpc.observability.RpcTracingBridge;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenTelemetry Peach RPC 分布式 Trace Bridge。
 */
public final class OpenTelemetryRpcTracingBridge
        implements RpcTracingBridge {

    private static final String INSTRUMENTATION_NAME =
            "io.peach.rpc";
    private static final TextMapGetter<Map<String, String>>
            GETTER = new TextMapGetter<>() {
                @Override
                public Iterable<String> keys(
                        Map<String, String> carrier) {
                    return carrier.keySet();
                }

                @Override
                public String get(
                        Map<String, String> carrier,
                        String key) {
                    return carrier.get(key);
                }
            };

    private final Tracer tracer;
    private final TextMapPropagator propagator;

    /**
     * 创建 OpenTelemetry Trace Bridge。
     *
     * @param openTelemetry OpenTelemetry
     */
    public OpenTelemetryRpcTracingBridge(
            OpenTelemetry openTelemetry) {
        Objects.requireNonNull(
                openTelemetry,
                "openTelemetry");
        this.tracer = openTelemetry.getTracer(
                INSTRUMENTATION_NAME);
        this.propagator = openTelemetry
                .getPropagators()
                .getTextMapPropagator();
    }

    @Override
    public RpcTraceContext startClient(
            ServiceKey serviceKey,
            int methodId) {
        Context parent = Context.current();
        Span span = tracer.spanBuilder(
                        serviceKey.serviceName()
                                + "/"
                                + methodId)
                .setParent(parent)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(
                        "rpc.system",
                        "peach-rpc")
                .setAttribute(
                        "rpc.service",
                        serviceKey.canonicalName())
                .setAttribute(
                        "rpc.method.id",
                        methodId)
                .startSpan();
        Context context = parent.with(span);
        Map<String, String> metadata =
                new LinkedHashMap<>();
        propagator.inject(
                context,
                metadata,
                Map::put);
        return new OtelTraceContext(
                span,
                context,
                Map.copyOf(metadata));
    }

    @Override
    public RpcTraceContext startServer(
            int serviceId,
            int methodId,
            Map<String, String> metadata) {
        Context parent = propagator.extract(
                Context.root(),
                metadata,
                GETTER);
        Span span = tracer.spanBuilder(
                        serviceId + "/" + methodId)
                .setParent(parent)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(
                        "rpc.system",
                        "peach-rpc")
                .setAttribute(
                        "rpc.service.id",
                        serviceId)
                .setAttribute(
                        "rpc.method.id",
                        methodId)
                .startSpan();
        return new OtelTraceContext(
                span,
                parent.with(span),
                Map.of());
    }

    private static final class OtelTraceContext
            implements RpcTraceContext {

        private final Span span;
        private final Context context;
        private final Map<String, String> metadata;
        private final AtomicBoolean ended =
                new AtomicBoolean();

        private OtelTraceContext(
                Span span,
                Context context,
                Map<String, String> metadata) {
            this.span = span;
            this.context = context;
            this.metadata = metadata;
        }

        @Override
        public Map<String, String> metadata() {
            return metadata;
        }

        @Override
        public RpcMetadataScope makeCurrent() {
            Scope scope = context.makeCurrent();
            return scope::close;
        }

        @Override
        public void end(
                RpcStatus status,
                Throwable error) {
            if (!ended.compareAndSet(false, true)) {
                return;
            }
            span.setAttribute(
                    "rpc.status",
                    status.name());
            if (error != null) {
                span.recordException(error);
                span.setStatus(StatusCode.ERROR);
            } else if (status != RpcStatus.OK) {
                span.setStatus(StatusCode.ERROR);
            } else {
                span.setStatus(StatusCode.OK);
            }
            span.end();
        }
    }
}
