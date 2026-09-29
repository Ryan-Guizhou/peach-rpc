# Peach RPC V2-C.3 Production Security & Observability 项目计划书

> 状态：**Proposed / Implementation Baseline**  
> 适用分支：`feature/v2c3-production-security-observability`  
> 前置基线：V2-C.2 已完成并合入 `main`，Connection & Control-plane HA 已形成自动化闭环。  
> 本文是 V2-C.3 的实施与验收基线；完成后同步更新 Production Roadmap、Readiness、README 与架构文档。

## 1. Summary

V2-C.3 的目标不是继续增加 Registry/Codec 数量，而是把 V2-C.2 已经具备的高可用运行时变成**可安全部署、可诊断、可追踪、可在线换证**的生产 RPC 基础设施。

本阶段一次性完成三条连续主线：

1. **V2-C.3A — Observability Foundation**
   - 扩展低依赖 `RpcObserver`；
   - 增加 Registry 生命周期与恢复事件；
   - 新增 Micrometer Adapter；
   - 建立低基数标准指标模型。
2. **V2-C.3B — TLS / mTLS**
   - Transport 层 TLS/mTLS；
   - CA/服务端/客户端证书校验；
   - Hostname Verification；
   - 独立 TLS handshake timeout；
   - PEM 证书热更新与过期检测；
   - TLS/mTLS 故障自动化测试。
3. **V2-C.3C — Distributed Tracing & Runtime Diagnostics**
   - OpenTelemetry Adapter；
   - `traceparent/tracestate/baggage` 跨 RPC metadata 传播；
   - Consumer/Provider Span；
   - JFR Adapter；
   - Observer/Adapter 故障隔离。

完成后，Peach RPC 仍保持 Preview；V2-D 性能内核第二次升级和 V2-E Wire Compatibility 仍是后续 Production GA 门禁。

---

## 2. Current State

V2-C.2 当前已具备：

- Vert.x TCP 长连接、多路复用与 connection-local Request ID；
- HELLO/HELLO_ACK；
- PING/PONG Heartbeat、idle detection 与 half-open connection 摘除；
- request-driven single-flight reconnect + exponential backoff + full jitter；
- absolute Deadline + relative timeout budget；
- Retry Budget / Outlier Ejection / Circuit Breaker；
- CANCEL propagation；
- Provider admission / execution policy / Graceful Drain；
- Etcd compaction/restart/leader-transfer 恢复；
- Nacos restart/re-registration/re-subscribe；
- 独立 JVM recovery E2E；
- Core `RpcObserver` 已覆盖 RPC invocation 和 connection lifecycle。

当前明显缺口：

- Transport 仍是明文 TCP；
- Provider 无法验证 Consumer 服务身份；
- RpcObserver 尚未覆盖 Registry/TLS 生命周期；
- 没有标准 Micrometer metrics；
- 没有跨进程 Trace Context；
- 没有 OpenTelemetry/JFR 具体 Adapter；
- 证书轮换只能依赖外部重启，因为 Transport 尚无证书 reload；
- 生产排障仍依赖日志和测试，而不是统一指标/Trace/JFR。

---

## 3. Goals

### 3.1 Security

- 支持 `PLAINTEXT / TLS / MTLS` 三种模式；
- 默认仍为 `PLAINTEXT`，保证现有用户零配置兼容；
- TLS 模式下 Consumer 校验 Provider；
- mTLS 模式下双方都校验证书；
- 默认启用 hostname verification；
- 证书错误、错误 CA、缺失客户端证书均 fail-fast；
- 支持 PEM certificate/key/trust material；
- 支持文件变更后的在线 reload；
- 新证书仅影响新连接，旧连接自然 drain/reconnect；
- 证书过期与临近过期可观测；
- 私钥内容、密码、Credential 不进入日志和异常文本。

### 3.2 Observability

- Core 继续零 Micrometer/OpenTelemetry/JFR 依赖；
- 默认 NOOP 路径不创建事件对象；
- Micrometer 提供稳定、低基数指标；
- Registry register/unregister/lookup/subscribe/recovery 纳入 Observer；
- Connection/TLS/Registry/Client/Server 事件具有统一状态语义；
- Observer 异常永不影响 RPC 数据面和控制面。

### 3.3 Tracing

- OpenTelemetry 类型不进入 Core 公共协议；
- 新增低依赖 RPC metadata propagation SPI；
- 支持 W3C Trace Context/Baggage；
- Consumer Span 与 Provider Span 可通过跨进程 metadata 建立父子关系；
- 未安装 OTel Adapter 时，不增加 metadata 和热路径对象；
- Trace propagation 不改变协议 Header，仅使用既有 metadata 区域。

### 3.4 Runtime Diagnostics

- JFR 记录慢调用、Retry、Heartbeat timeout、Reconnect、Registry recovery 等低频高价值事件；
- JFR 未开启 recording 时不影响主链；
- 不把高频每包/每帧事件写入 JFR。

---

## 4. Non-goals

本阶段明确不做：

- 服务级授权/ACL；mTLS 负责服务身份，不替代业务 Permission/Internal Token；
- 自研加密协议；
- Service Mesh/Istio/xDS；
- Streaming RPC；
- PKCS#12/JKS 第一优先实现；本阶段生产主路径以 PEM 为准；
- Fory Stable Type ID / Schema fingerprint；
- Buffer ownership / zero-copy 重构；
- 新增 ZooKeeper/Consul/Eureka；
- 完整容量规划与性能内核第二次升级。

PKCS#12/JKS 可在 PEM 主路径稳定后作为兼容扩展，不阻塞本阶段完成。

---

## 5. Architectural Decisions

### ADR-1：TLS 只存在于 Transport Adapter

```text
PeachRpcClient / PeachRpcServer
        |
Peach RPC Protocol
        |
RpcTransport SPI
        |
Vert.x TLS/mTLS
        |
TCP
```

Core 可以拥有与实现无关的 `RpcTransportSecurityOptions`，但不得暴露 Vert.x `KeyCertOptions`、`TrustOptions` 等类型。

### ADR-2：TLS 在 Peach RPC HELLO 之前完成

```text
TCP connect
  -> TLS/mTLS handshake
  -> certificate verification
  -> Peach HELLO
  -> HELLO_ACK
  -> ACTIVE
```

TLS 不成功，不进入 Peach Protocol。

### ADR-3：证书热更新使用 Vert.x SSL option update

文件变更后：

```text
new PEM
  -> validate
  -> updateSSLOptions
  -> new connections use new material
  -> old connections continue
  -> normal reconnect/drain migrates traffic
```

不主动一次性断开所有旧连接，避免换证制造连接风暴。

### ADR-4：观测 Adapter 独立模块

新增模块：

- `peach-rpc-observability-micrometer`
- `peach-rpc-observability-opentelemetry`
- `peach-rpc-observability-jfr`

依赖方向：

```text
peach-rpc-core
     ^
     |
RpcObserver / Metadata Propagation SPI
     ^
     +-- Micrometer Adapter
     +-- OpenTelemetry Adapter
     +-- JFR Adapter
```

Core 不反向依赖 Adapter。

### ADR-5：Metrics 不使用高基数 endpoint/service instance 标签

Micrometer 默认标签只使用：

- role；
- status；
- execution mode；
- close reason；
- registry type；
- registry operation/recovery action；
- security mode。

不默认把：

- IP/port；
- instanceId；
- exception message；
- traceId；
- user metadata

作为指标标签。

### ADR-6：Trace Metadata 使用既有 v1 metadata 区域

W3C propagation 使用：

- `traceparent`
- `tracestate`
- `baggage`

不修改 32-byte fixed header，不升级 protocol version。

只有 Propagator enabled 时才编码这些 metadata。

---

## 6. Proposed Module Layout

完成后顶层模块：

```text
peach-rpc-core
peach-rpc-codegen
peach-rpc-codec-fory
peach-rpc-transport-vertx
peach-rpc-registry-etcd
peach-rpc-registry-nacos
peach-rpc-proxy-cglib
peach-rpc-proxy-bytebuddy
peach-rpc-observability-micrometer
peach-rpc-observability-opentelemetry
peach-rpc-observability-jfr
peach-rpc-spring-boot-autoconfigure
peach-rpc-spring-boot-starter
peach-rpc-examples
peach-rpc-benchmarks
```

新增模块不是概念拆分，而是用于隔离第三方依赖。

---

## 7. Core Contracts

### 7.1 Registry Observation

扩展 `RpcObserver`：

- registry operation completed；
- registry recovery completed；
- TLS handshake completed；
- certificate reload completed；
- certificate expiry warning。

新增低依赖枚举：

- `RpcRegistryOperation`
- `RpcRegistryRecoveryAction`
- `RpcSecurityMode`
- `RpcCertificateReloadOutcome`

### 7.2 Metadata Propagation

新增：

```java
RpcMetadataPropagator
RpcMetadataScope
```

职责：

- Consumer 调用开始时注入 metadata；
- Provider 收到请求时恢复上下文；
- Scope 在业务执行结束后关闭；
- Composite Propagator 隔离单个实现异常；
- NOOP 不分配 Map。

### 7.3 Protocol Metadata

保留 deadline 快路径。

当 Propagator disabled：

```text
deadlineEpochMillis
timeoutBudgetMillis
```

当 enabled：

```text
deadlineEpochMillis
timeoutBudgetMillis
traceparent
tracestate
baggage
```

对 metadata key/value 做长度、字符和总大小限制。

---

## 8. TLS / mTLS Configuration

计划新增：

```yaml
peach:
  rpc:
    transport:
      security:
        mode: mtls
        certificate-path: /etc/peach-rpc/tls/tls.crt
        private-key-path: /etc/peach-rpc/tls/tls.key
        trust-certificate-path: /etc/peach-rpc/tls/ca.crt
        hostname-verification: true
        handshake-timeout: 3s
        reload-interval: 30s
        expiry-warning-threshold: 7d
```

规则：

| Mode | Client cert | Server cert | Trust CA | Provider verifies Client |
|---|---:|---:|---:|---:|
| PLAINTEXT | - | - | - | - |
| TLS | optional/no | required | required | no |
| MTLS | required | required | required | yes |

Provider 和 Consumer 可使用同一配置模型，但验证规则按角色执行。

---

## 9. Certificate Lifecycle

启动时：

1. 校验文件存在且可读；
2. 解析 X.509 certificate；
3. 执行 `checkValidity()`；
4. 检查 notAfter；
5. 构造 Vert.x SSL options；
6. TLS/mTLS 握手后才进入 HELLO。

运行时：

1. 周期读取 cert/key/trust 文件 fingerprint/mtime；
2. 变化时先在内存中完整解析；
3. 解析/校验失败则保留旧证书；
4. 校验成功后调用 Vert.x `updateSSLOptions`；
5. 新连接使用新证书；
6. 记录 reload success/failure；
7. 临近过期产生 warning event，不记录证书私钥/内容。

---

## 10. Micrometer Metrics

建议指标：

### Client

- `peach.rpc.client.attempts` Timer
- `peach.rpc.client.retries` Counter
- `peach.rpc.client.failures` Counter

### Server

- `peach.rpc.server.invocations` Timer
- `peach.rpc.server.failures` Counter
- `peach.rpc.server.overloaded` Counter

### Connection

- `peach.rpc.connection.active` Gauge
- `peach.rpc.connection.established` Timer
- `peach.rpc.connection.reconnects` Counter
- `peach.rpc.connection.heartbeat.timeouts` Counter
- `peach.rpc.connection.closed` Counter

### Registry

- `peach.rpc.registry.operations` Timer
- `peach.rpc.registry.failures` Counter
- `peach.rpc.registry.recoveries` Timer

### TLS

- `peach.rpc.tls.handshake` Timer
- `peach.rpc.tls.handshake.failures` Counter
- `peach.rpc.tls.certificate.reload` Counter
- `peach.rpc.tls.certificate.expiry` Gauge/low-frequency event

---

## 11. OpenTelemetry

### 11.1 Consumer

```text
application span
   -> Peach RPC client span
       -> inject traceparent/tracestate/baggage
       -> RPC transport
```

Span attributes采用低风险语义：

- rpc.system = peach-rpc
- rpc.service
- rpc.method.id
- server.address
- server.port
- rpc.status

### 11.2 Provider

```text
extract W3C context
   -> Peach RPC server span
       -> Provider business invocation
```

业务异常标记 Span status，但不把原始敏感业务错误 message 强制写入 attribute。

---

## 12. JFR

JFR Adapter 只记录高价值事件：

- Client slow/failed attempt；
- Retry scheduled；
- Provider slow/failed invocation；
- Connection reconnect；
- Heartbeat timeout；
- TLS handshake failure；
- Registry recovery；
- Certificate reload failure。

提供阈值，避免正常高频 RPC 全量写 JFR。

---

## 13. Spring Boot Integration

Adapter 自身提供 Spring Boot AutoConfiguration：

### Micrometer

条件：

- Adapter 在 classpath；
- `MeterRegistry` Bean 存在。

### OpenTelemetry

条件：

- Adapter 在 classpath；
- `OpenTelemetry` Bean 存在。

### JFR

条件：

- Adapter 在 classpath；
- `peach.rpc.observability.jfr.enabled=true`。

Starter 不强制携带三种 Adapter，避免所有用户被迫引入第三方观测栈。

---

## 14. Testing Strategy

### 14.1 Core

- Observer composite failure isolation；
- Registry event composite；
- Propagator NOOP / composite；
- metadata validation；
- trace metadata encode/decode；
- unknown metadata backward compatibility。

### 14.2 Micrometer

- Counter/Timer/Gauge 数值；
- 标签基数；
- Observer 异常不传播。

### 14.3 OpenTelemetry

使用 SDK test exporter：

- Consumer Span；
- Provider Span；
- parent/child traceId 一致；
- traceparent round-trip；
- error status；
- NOOP/no provider 不产生 metadata。

### 14.4 JFR

- Event 类型可 commit；
- threshold 行为；
- disabled recording 不影响调用。

### 14.5 TLS

至少覆盖：

1. TLS valid CA success；
2. wrong CA fail；
3. hostname mismatch fail；
4. mTLS success；
5. mTLS missing client certificate fail；
6. invalid/expired certificate fail-fast；
7. certificate reload success；
8. invalid replacement certificate 保留旧 material；
9. plaintext regression；
10. TLS + Heartbeat + reconnect 共存。

测试证书只能是测试 fixture，不包含生产 secret。

---

## 15. Compatibility

默认：

```text
security.mode = PLAINTEXT
no observability adapter
no metadata propagator
```

因此旧应用升级后行为不变。

Trace metadata 使用既有 metadata area，旧 Provider 会忽略未知 key。

TLS 与 plaintext 不能在同一端口自动探测；配置错误应快速失败，不尝试静默 downgrade。

---

## 16. Security Rules

- 禁止 `trust-all=true` 作为生产配置；
- hostname verification 默认开启；
- mTLS server 必须 `ClientAuth.REQUIRED`；
- 不把 private key/password/certificate raw content 打日志；
- 异常只输出安全的 path/alias 信息；
- replacement certificate 验证失败时继续使用旧 material；
- TLS 失败不得 fallback plaintext；
- Trace/Baggage metadata 必须做大小限制。

---

## 17. Rollout / Rollback

### Plaintext -> TLS

不能直接混用同一端口。

推荐：

1. 新 Provider 暴露 TLS 端口；
2. Registry 发布 TLS-capable service version/group；
3. Consumer 切换；
4. 观察连接与 TLS 指标；
5. 最后关闭 plaintext。

### TLS -> mTLS

1. 先让 Provider trust CA，但仍不 required client auth；
2. 所有 Consumer 配置 client cert；
3. 验证 client certificate handshake；
4. Provider 切为 MTLS required。

Rollback 始终通过版本/group/端口切换完成，不做协议级 silent downgrade。

---

## 18. Implementation Sequence

### Phase A — Foundation

1. 落本计划书；
2. Core Registry/TLS Observer contracts；
3. metadata propagation SPI；
4. Protocol metadata 扩展；
5. RegistryOptions observer plumbing；
6. Etcd/Nacos Registry observation。

### Phase B — Micrometer

1. 新模块；
2. standard metrics；
3. Spring AutoConfiguration；
4. unit tests。

### Phase C — TLS/mTLS

1. `RpcTransportSecurityOptions`；
2. Spring properties；
3. Vert.x TLS client/server；
4. mTLS client auth；
5. hostname verification；
6. certificate parser/validation；
7. live reload；
8. TLS tests。

### Phase D — OpenTelemetry

1. OTel propagator；
2. client/server span；
3. W3C metadata；
4. Spring AutoConfiguration；
5. cross-process context tests。

### Phase E — JFR

1. JFR events；
2. Observer adapter；
3. thresholds；
4. tests。

### Phase F — Integration Closure

1. Examples；
2. README 中文/英文；
3. architecture；
4. starter；
5. readiness；
6. production-roadmap；
7. `check_project.py`；
8. `mvn -B -ntp clean verify -Pquality`；
9. TLS/mTLS E2E；
10. existing Nacos/Etcd Chaos regression。

---

## 19. Acceptance Gates

V2-C.3 只有在以下全部满足后才能标记 Current：

### Observability

- [ ] Registry lifecycle Observer；
- [ ] Micrometer Adapter；
- [ ] OpenTelemetry Adapter；
- [ ] W3C trace propagation；
- [ ] JFR Adapter；
- [ ] Adapter failure isolation。

### Security

- [ ] TLS；
- [ ] mTLS；
- [ ] CA verification；
- [ ] hostname verification；
- [ ] handshake timeout；
- [ ] certificate expiry validation；
- [ ] certificate reload；
- [ ] invalid reload keeps old certificate；
- [ ] no plaintext fallback。

### Quality

- [ ] Core unit tests；
- [ ] Adapter tests；
- [ ] TLS/mTLS integration tests；
- [ ] plaintext regression；
- [ ] Chinese/English README parity；
- [ ] `python3 scripts/check_project.py`；
- [ ] `mvn -B -ntp clean verify -Pquality`；
- [ ] existing Nacos recovery CI remains green；
- [ ] Etcd Chaos remains green。

---

## 20. Risks

| 风险 | 缓解 |
|---|---|
| TLS 增加连接建立延迟 | 长连接摊薄；独立 handshake timeout；Micrometer 监控 |
| 证书 reload 造成连接风暴 | 只更新新连接 material，不主动断全部旧连接 |
| Observer/Tracing 增加热路径分配 | NOOP fast path；仅 Adapter enabled 时创建 metadata/span |
| Metrics 标签爆炸 | 默认禁止 endpoint/instanceId/error-message 标签 |
| Baggage 过大 | metadata 总长/key/value 上限 |
| mTLS 配置错误导致全链路不可用 | fail-fast + 独立 E2E + 分阶段 rollout |
| OTel Adapter 影响数据面 | Core 隔离异常；Exporter 不进入 RPC Event Loop |
| 新模块膨胀 | 仅按第三方依赖边界拆分，不拆概念模块 |

---

## 21. Completion Definition

本计划完成后的项目定位：

> Peach RPC 已具备生产级连接/控制面 HA、TLS/mTLS 安全通道、服务双向身份校验、证书在线轮换，以及 Metrics/Tracing/JFR 三层可观测基础。

但仍不宣称 Production GA；下一阶段进入 V2-D Performance Kernel Second Pass，然后 V2-E Wire Compatibility / Stable Schema。
