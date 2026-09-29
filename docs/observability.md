# Peach RPC 可观测性指南

> V2-C.3 提供三层可观测 Adapter：Micrometer、OpenTelemetry、JFR。  
> Core 本身不直接依赖上述框架。

## 1. 架构边界

~~~text
peach-rpc-core
   |
   +-- RpcObserver
   +-- RpcTracingBridge
   +-- RpcMetadataPropagator
            |
            +-- Micrometer Adapter
            +-- OpenTelemetry Adapter
            +-- JFR Adapter
~~~

未安装 Adapter 时，Core 使用 NOOP 实现。

## 2. Micrometer

依赖：

~~~xml
<dependency>
    <groupId>io.peach.rpc</groupId>
    <artifactId>peach-rpc-observability-micrometer</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
~~~

当 Spring Context 中存在 `MeterRegistry` 时自动创建 RPC Observer。

### 标准指标

主要指标：

- `peach.rpc.client.attempts`
- `peach.rpc.client.retries`
- `peach.rpc.server.invocations`
- `peach.rpc.connection.active`
- `peach.rpc.connection.established`
- `peach.rpc.connection.reconnects`
- `peach.rpc.connection.heartbeat.timeouts`
- `peach.rpc.connection.closed`
- `peach.rpc.registry.operations`
- `peach.rpc.registry.recoveries`
- `peach.rpc.tls.handshake`
- `peach.rpc.tls.certificate.reload`
- `peach.rpc.tls.certificate.expiry.warnings`

默认不会把 endpoint、instanceId、异常 message、traceId 作为标签。

## 3. OpenTelemetry

依赖：

~~~xml
<dependency>
    <groupId>io.peach.rpc</groupId>
    <artifactId>peach-rpc-observability-opentelemetry</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
~~~

Spring 中存在 `OpenTelemetry` Bean 时自动提供 `RpcTracingBridge`。

### Trace 流程

~~~mermaid
sequenceDiagram
    participant A as Application
    participant C as PeachRpcClient
    participant W as Wire Metadata
    participant S as PeachRpcServer
    participant B as Business

    A->>C: invoke
    C->>C: create CLIENT span
    C->>W: inject traceparent/tracestate/baggage
    W->>S: RPC request
    S->>S: extract remote context
    S->>B: activate SERVER span context
    B-->>S: result
    S-->>C: response
    S->>S: end SERVER span
    C->>C: end CLIENT span
~~~

当前真实 RPC E2E 会验证：

- Consumer/Provider 通过 Vert.x 真实 RPC 通信；
- CLIENT/SERVER Span 具有相同 Trace ID；
- SERVER Span parent 为 CLIENT Span。

Trace metadata 使用现有 v1 metadata area，因此不改变固定 Header。

## 4. Metadata 安全限制

额外 Metadata：

- key/value 不允许换行；
- key 不允许 `=`；
- key/value 有长度上限；
- 总 metadata 最大 65535 bytes；
- `deadlineEpochMillis` 和 `timeoutBudgetMillis` 为保留键，不允许扩展覆盖。

默认没有 Trace Adapter 时不会创建额外 Trace Metadata。

## 5. JFR

依赖：

~~~xml
<dependency>
    <groupId>io.peach.rpc</groupId>
    <artifactId>peach-rpc-observability-jfr</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
~~~

启用：

~~~yaml
peach:
  rpc:
    observability:
      jfr:
        enabled: true
        slow-threshold: 100ms
~~~

JFR 只记录低频高价值事件：

- 慢/失败 Client attempt；
- 慢/失败 Provider invocation；
- reconnect；
- heartbeat timeout；
- Registry recovery；
- TLS handshake failure/slow handshake；
- certificate reload。

正常高频 RPC 不会无条件写 JFR Event。

## 6. Registry Observability

Etcd 记录：

- register/unregister/lookup/subscribe；
- Lease registration recovery；
- subscription/watch recovery。

Nacos 记录：

- register/unregister/lookup/subscribe。

Nacos SDK 内部 reconnect/redo 由 SDK 自己管理，因此 Peach RPC 不伪造无法精确观测的底层 reconnect 事件；其恢复正确性由独立 JVM + Nacos restart E2E 验证。

## 7. 故障隔离

Observer/Propagator Adapter 必须满足：

> telemetry failure must not become RPC failure.

Core Composite 会隔离 Adapter 回调异常。

OpenTelemetry exporter、Micrometer registry、JFR recording 都不应成为数据面成功与否的决定条件。
