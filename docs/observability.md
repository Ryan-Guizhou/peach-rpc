# OTRYX RPC 可观测性

> 状态：**2.0.0-SNAPSHOT Migration**  
> Core 不直接依赖 Micrometer、OpenTelemetry 或 JFR。

## 1. 架构边界

```mermaid
flowchart LR
    Core[otryx-core]
    Observer[RpcObserver]
    Trace[RpcTracingBridge]
    Meta[RpcMetadataPropagator]
    Micro[Micrometer Adapter]
    OTel[OpenTelemetry Adapter]
    Jfr[JFR Adapter]

    Core --> Observer
    Core --> Trace
    Core --> Meta
    Observer --> Micro
    Trace --> OTel
    Observer --> Jfr
```

未安装 Adapter 时使用 NOOP 路径。

## 2. Micrometer

依赖：

```xml
<dependency>
    <groupId>com.peachsoft.otryx</groupId>
    <artifactId>otryx-observability-micrometer</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

Spring Context 中存在 `MeterRegistry` 时可自动装配 RPC Observer。

### 标准指标

- `otryx.rpc.client.calls`；
- `otryx.rpc.client.attempts`；
- `otryx.rpc.client.retries`；
- `otryx.rpc.client.retry.exhausted`；
- `otryx.rpc.client.inflight`；
- `otryx.rpc.client.timeouts`；
- `otryx.rpc.client.circuit.rejected`；
- `otryx.rpc.client.circuit.state`；
- `otryx.rpc.client.outlier.ejected`；
- `otryx.rpc.server.invocations`；
- `otryx.rpc.server.inflight`；
- `otryx.rpc.server.admission.rejected`；
- `otryx.rpc.server.overloaded`；
- `otryx.rpc.connection.active`；
- `otryx.rpc.connection.established`；
- `otryx.rpc.connection.reconnects`；
- `otryx.rpc.connection.heartbeat.timeouts`；
- `otryx.rpc.connection.closed`；
- `otryx.rpc.registry.operations`；
- `otryx.rpc.registry.failures`；
- `otryx.rpc.registry.recoveries`；
- `otryx.rpc.tls.handshake`；
- `otryx.rpc.tls.certificate.reload`；
- `otryx.rpc.tls.certificate.expiry.warnings`。

默认不把 endpoint、instanceId、service、method、exception message、traceId 放进 Meter Tag，避免高基数。

## 3. Call 与 Attempt

一次业务调用可能产生多个网络 Attempt：

```text
client.calls = 1
client.attempts = 1..N
client.retries = attempts - 1
```

业务 QPS、成功率、Latency SLO 以 logical call 为主；Attempt 用于诊断 Retry Amplification。

## 4. OpenTelemetry

依赖：

```xml
<dependency>
    <groupId>com.peachsoft.otryx</groupId>
    <artifactId>otryx-observability-opentelemetry</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

真实 RPC 路径传播 W3C Trace Context/Baggage。

```mermaid
sequenceDiagram
    participant A as Application
    participant C as Consumer
    participant W as Wire Metadata
    participant P as Provider

    A->>C: invoke
    C->>C: CLIENT span
    C->>W: inject context
    W->>P: REQUEST
    P->>P: extract + SERVER span
    P-->>C: RESPONSE
    P->>P: end SERVER span
    C->>C: end CLIENT span
```

## 5. JFR

依赖：

```xml
<dependency>
    <groupId>com.peachsoft.otryx</groupId>
    <artifactId>otryx-observability-jfr</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

启用示例：

```yaml
otryx:
  rpc:
    observability:
      jfr:
        enabled: true
        slow-threshold: 100ms
```

JFR 用于低频高价值诊断：慢/失败调用、Retry、reconnect、heartbeat timeout、Registry recovery、TLS/证书事件。

## 6. Metadata 安全边界

- key/value 不允许换行；
- key 不允许 `=`；
- key/value 有长度限制；
- 总 metadata 最大 65535 bytes；
- Deadline/Timeout Budget 为保留键；
- 未启用 Trace Adapter 时不创建额外 Trace Metadata。

## 7. 故障隔离

> telemetry failure must not become RPC failure.

Observer/Propagator Adapter 回调异常必须被隔离，不得改变 RPC 业务成功与否。

## 8. 生产入口

- [生产可观测与 SLO](reference/production-observability.md)
- [Grafana Dashboard](../deploy/observability/grafana/otryx-dashboard.json)
- [Prometheus Alert Example](../deploy/observability/prometheus/otryx-alerts.example.yml)
