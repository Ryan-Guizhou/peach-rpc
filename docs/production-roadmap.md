# Peach RPC Production Roadmap / Capability Matrix

<!-- capability-status:project=preview -->
<!-- capability-status:v2-c3=current -->
<!-- capability-status:v2-d=in-progress -->
<!-- capability-status:v2-d2=in-progress -->

> 本文是 Peach RPC **生产能力状态与后续优先级的唯一总表**。  
> V2-A、V2-B、V2-B.1、V2-B.2、V2-C.x 等阶段文档继续保留，用于记录具体设计与历史决策；当“当前状态”和旧阶段文档发生冲突时，以本文与实际代码/测试为准。

## 1. 文档口径

本文围绕三个目标评估 Peach RPC：

1. **High Availability**：故障可检测、可隔离、可恢复，恢复过程有边界且不会放大故障；
2. **High Performance**：热路径低开销、资源有界、尾延迟可控，并且性能结论有可重复基准支撑；
3. **Production Readiness**：安全、可观测、兼容、测试、升级回滚和容量规划形成闭环。

### 1.1 状态定义

| 状态 | 含义 |
|---|---|
| **Current** | 当前文档所在分支已有实现，并有对应自动化测试或可运行验证 |
| **Partial** | 已有核心骨架或部分实现，但生产闭环仍缺关键能力/验证 |
| **Proposed** | 已形成下一阶段方向，但当前代码尚未实现 |
| **Future** | 属于长期生态或能力扩展，不应阻塞近期高可用/高性能主线 |
| **Optional** | 是否实现取决于项目范围，不作为当前 Production GA 的必选门禁 |

> V2-C.1、V2-C.2、V2-C.3 已进入主线。V2-D 第一批已进入主线，V2-D.2 当前正在建立完整性能矩阵、allocation/GC profiling 与 10k logical-concurrency soak；本文中的 **Current** 表示已经进入主线并有对应自动化验证，**In Progress** 表示工程入口已实现但生产证据仍在收集。

---

## 2. 当前总体判断

Peach RPC 已经完成第一阶段的高可用和高性能内核骨架：

- 请求热路径不访问 Registry；
- Generated Consumer Stub / Provider Dispatcher 已成为高性能主路径；
- Vert.x 长连接、多路复用、连接分片、握手和 GO_AWAY 已落地；
- Retry Budget、显式幂等重试、Outlier Ejection、Circuit Breaker、CANCEL、Graceful Drain 已进入真实数据面；
- Provider 执行模型已经区分 BLOCKING_VIRTUAL / CPU / guarded DIRECT；
- Etcd 具备 Lease 丢失后的重新注册恢复与 Watch 重订阅退避；
- Nacos 3.2.4 Adapter 已完成注册、发现、订阅、过滤与真实 RPC round-trip；
- Core 已提供低依赖 `RpcObserver` 观测契约；
- JMH 已覆盖调用、协议、负载均衡与基础端到端路径。

V2-C.3 已经补齐 TLS/mTLS、PEM 证书生命周期、Micrometer、OpenTelemetry 与 JFR 三层可观测基础。V2-D 第一批热路径优化已经进入主线，V2-D.2 正在补完整性能证据与 10k soak。项目仍定位为 **Preview**。距离“可作为中型项目默认 RPC 层”的主要缺口现在集中在：

1. Fory Stable Type ID / Schema fingerprint / 滚动升级兼容；
2. byte[] / Object[] 等剩余热路径分配与完整性能矩阵；
3. 网络黑洞/分区、协议 fuzz / malformed frame 与长时间 soak；
4. Registry Contract TestKit 与更完整的控制面故障矩阵；
5. 容量规划、升级、回滚和兼容矩阵；
6. Production SLO、Dashboard 与告警模板。

---

## 3. 能力矩阵

### 3.1 数据面与高性能

| 能力 | 状态 | 当前实现 | 生产缺口 / 下一步 |
|---|---|---|---|
| Vert.x TCP 长连接 | **Current** | 单 Endpoint 可维护连接分片 | 继续由压力测试验证连接数与吞吐关系 |
| Connection-local Request ID | **Current** | Request ID 与 Pending Table 按连接本地化 | 继续验证极高并发下 wrap / reconnect 边界 |
| RPC 多路复用 | **Current** | 单连接多 inflight 请求 | 增加 1k/10k 并发稳定性矩阵 |
| HELLO / HELLO_ACK | **Current** | Protocol/Codec/Compression/Feature 协商，独立 handshake timeout | 增加 rolling compatibility 与恶意握手测试 |
| Generated Consumer Stub | **Current** | `@PeachRpcContract` 编译期生成，运行时优先使用 | 扩展更多参数形态和兼容验证 |
| Generated Provider Dispatcher | **Current** | 生成路径优先，MethodHandle fallback | 建立 Generated vs MethodHandle 完整基准 |
| 0~4 参数专用 CallSite | **Current** | 避免通用动态调用主路径 | Fory 参数对象图仍存在 `Object[]` |
| RpcMethodCodec 预绑定 | **Current** | refer/register 阶段完成方法级绑定 | 为未来 Buffer-oriented Codec 保留兼容边界 |
| 本地 ServiceDirectory | **Current** | Registry snapshot 转换为数组快照 | 继续保持 Registry 不进入请求热路径 |
| P2C + EWMA | **Current** | 直接读取数组与 EndpointStats | 增加大规模 Endpoint 数量基准 |
| Payload slice decode | **Current** | `RpcFrameView` 保留 backing byte[] + offset/length | Transport/Core 仍以完整 byte[] frame 为主要边界 |
| Unary fast encode | **Current** | REQUEST/RESPONSE 专用编码路径 | 继续减少中间 byte[] 与通用对象 |
| Buffer ownership | **Partial** | 已有 frame view / slice 基础 | 尚未形成 OwnedBuffer/retain/release 或等价 Buffer API；是否进入默认路径必须由基准决定 |
| 消除参数 Object[] | **Partial** | Generated CallSite 已减少动态调用 | Fory Codec ID 1 仍使用 Object[] 参数对象图 |
| Compression 数据面 | **Proposed** | Wire ID 已预留 NONE/LZ4/ZSTD | 当前实际只启用 NONE；必须以 payload/CPU/带宽基准决定策略 |
| 完整性能矩阵 | **Partial** | 已有 payload/security/provider-scenario/resilience 四类 Matrix、sample/throughput、GC profiler、证据校验与 CI smoke | 固定硬件执行 full matrix，并形成可重复数值基线 |
| 性能容量模型 | **Partial** | 已形成 Capacity Planning 方法论、QPS/Core/实例数公式、Payload/TLS 分桶与决策模板；暂无固定硬件生产数值 | 在固定硬件填充 p50/p99/p99.9、CPU、Allocation、GC、错误率与连接数基线 |

### 3.2 Consumer 高可用与容错

| 能力 | 状态 | 当前实现 | 生产缺口 / 下一步 |
|---|---|---|---|
| Deadline / Timeout | **Current** | Consumer 逻辑 Deadline 覆盖 connect/handshake/request；Request 双写绝对 Deadline 与相对预算 | 继续补超时/取消竞态与慢连接测试 |
| CANCEL 传播 | **Current** | timeout / Future.cancel -> Transport CANCEL -> Provider cancel | 补更多竞态/重复 CANCEL/final response race 测试 |
| 显式幂等重试 | **Current** | 仅 `@PeachRpcIdempotent` 可自动 Retry | 持续保持非幂等默认不重试 |
| Retry Budget | **Current** | 全局预算 + maxAttempts + jitter backoff | 增加大规模故障时 retry storm 压测 |
| Outlier Ejection | **Current** | Endpoint 连续基础设施失败临时剔除 | 增加恢复和大规模 endpoint fault matrix |
| Circuit Breaker | **Current** | 方法级 CLOSED/OPEN/HALF_OPEN | 增加长时间 half-open / concurrent probe 验证 |
| P2C/EWMA 与故障状态融合 | **Current** | 被剔除实例不进入默认候选 | 增加大量实例/部分故障基准 |
| Last-known-good Directory | **Current** | 数据面持续读取本地快照；Nacos restart 的独立 JVM E2E 已验证控制面重启期间既有数据连接继续可用，恢复后再更新目录 | 继续补网络黑洞/分区与长时间 soak |
| Relative timeout budget | **Current** | 新 Request 同时携带 `timeoutBudgetMillis` 与旧 `deadlineEpochMillis`，新 Provider 优先相对预算语义 | Provider 运行中硬超时仍主要依赖 Consumer CANCEL；后续可评估服务端执行计时器 |

### 3.3 Provider 稳定性

| 能力 | 状态 | 当前实现 | 生产缺口 / 下一步 |
|---|---|---|---|
| Provider 全局 admission | **Current** | 最大并发有界 | 继续验证不同业务耗时下的 queueing/tail latency |
| BLOCKING_VIRTUAL | **Current** | 默认阻塞业务执行策略 | 增加高并发虚拟线程 + 下游阻塞场景 soak test |
| CPU bounded executor | **Current** | 固定线程数 + 有界队列；满时 OVERLOADED | 增加 saturation 与恢复基准 |
| DIRECT guarded mode | **Current** | 默认禁止，必须显式 allow | 保持为高级逃生口，不作为默认路径 |
| Graceful Drain | **Current** | unregister -> GO_AWAY -> wait inflight -> close | 增加进程级滚动发布验证 |
| Registry control-plane timeout | **Current** | register/rollback/unregister 有独立超时 | 继续验证 Registry 卡死/网络黑洞 |
| Dynamic advertised endpoint | **Current** | bind 地址与 Registry 发布地址分离 | 多网卡/NAT 场景依赖显式配置，不做自动猜测 |

### 3.4 连接级高可用

| 能力 | 状态 | 当前实现 | 生产缺口 / 下一步 |
|---|---|---|---|
| Connection handshake timeout | **Current** | 建连后 HELLO/ACK 有独立超时 | 已具备基础连接保护 |
| GO_AWAY | **Current** | graceful 与 fatal 语义分离 | 增加更多 race / reconnect 测试 |
| PING / PONG | **Current** | 作为 `RpcFeature.HEARTBEAT` 经 HELLO/ACK 协商后启用 | 增加更长时间 soak 与异常帧测试 |
| Idle detection | **Current** | Client/Server 对空闲连接发送 PING；任意有效入站帧可证明存活 | 增加网络黑洞级故障注入 |
| Reconnect backoff + jitter | **Current** | Consumer 使用 request-driven single-flight、指数退避 + full jitter、最大窗口 | V2-C.3 将当前 Core Observer 映射为标准 metrics/tracing |
| Half-open connection detection | **Current** | heartbeat timeout 后关闭 silent connection，后续请求重建连接 | 增加真实网络黑洞与 NAT 场景验证 |
| Connection recovery observability | **Current** | `RpcObserver` 已覆盖 connection established、reconnect scheduled、heartbeat timeout、closed，并携带 CLIENT/SERVER 角色与归一化关闭原因 | V2-C.3 增加 Micrometer/OpenTelemetry/JFR Adapter |

### 3.5 Registry / 控制面

| 能力 | 状态 | 当前实现 | 生产缺口 / 下一步 |
|---|---|---|---|
| Memory Registry | **Current** | 单 JVM 测试/开发 | 非分布式生产 Registry |
| Etcd register/discovery/watch | **Current** | Lease + Range/Watch；真实 compaction 后 Range+Watch 恢复、稳定逻辑目标 restart、独立 3 节点 leader transfer Chaos 均已验证 | 继续补网络黑洞/分区与长时间 soak |
| Etcd Lease recovery | **Current** | keepalive error/completed 为主信号；TTL watchdog 兜底识别 Lease 静默失效，grant 有界，stale lease callback 不会误伤新 Lease；restart 测试验证 active registrations 恢复 | 继续补长时间断链/黑洞 soak |
| Etcd Watch backoff | **Current** | 指数退避 + jitter；真实 stale revision + compaction 路径验证 error 后重新 Range 并从新 revision 建 Watch | 继续补网络分区与慢控制面场景 |
| Nacos register/lookup/subscribe | **Current** | Nacos 3.2.4，临时实例、Group/Cluster/metadata/weight；独立 JVM E2E 重启 Nacos 后验证既有数据面、Provider 临时实例重注册和 Consumer 重订阅/Endpoint 更新 | 继续补 auth-enabled、网络分区与 soak |
| Nacos SDK 隔离 | **Current** | 私有有界控制面线程池，不占用 Vert.x Event Loop | 增加 queue saturation 与 Registry 慢调用指标 |
| Registry Capability | **Current** | REGISTRATION/SUBSCRIPTION/... 能力模型 | 建立跨 Adapter Contract TestKit |
| Registry Contract TestKit | **Partial** | Core 有基础 Capability 契约，各 Adapter 有独立测试 | 抽出 Memory/Etcd/Nacos 通用行为矩阵 |
| ZooKeeper / Consul / Kubernetes / Eureka | **Future** | 尚未实现 | 不应早于 HA/Security/Compatibility 主线 |

### 3.6 安全

| 能力 | 状态 | 当前实现 | 生产缺口 / 下一步 |
|---|---|---|---|
| Registry credential 配置 | **Current** | Nacos username/password 只进入 SDK Properties；endpoint 禁止嵌入 Credential；RegistryOptions toString 自动脱敏 | 真实 auth-enabled Nacos Server 集成可作为后续增强 |
| TLS | **Current** | Vert.x Transport TLS、CA Trust、Hostname Verification、独立 TLS handshake timeout；wrong CA/hostname mismatch 真实网络测试 | 后续补更大规模 TLS 性能矩阵 |
| mTLS | **Current** | Provider ClientAuth.REQUIRED，双向证书校验；缺失 Client Cert 测试 | 后续与服务级授权模型结合 |
| 证书生命周期 | **Current** | X.509 有效期校验、过期 fail-fast、临期 warning、PEM 在线 Reload；SHA-256 内容指纹避免同大小/同 mtime 轮换漏检；无效 replacement 保留旧 material | 后续可扩展 PKCS#12/JKS 与集中证书管理 |
| TLS 可观测性 | **Current** | RpcObserver + Micrometer/JFR 覆盖 TLS handshake、reload、expiry warning；错误 CA 会产生 handshake failure event | 后续结合告警模板 |

### 3.7 可观测性

| 能力 | 状态 | 当前实现 | 生产缺口 / 下一步 |
|---|---|---|---|
| Core `RpcObserver` | **Current** | Client/Provider、Connection、Registry、TLS、Certificate 生命周期事件；Composite 隔离 Adapter 异常，NOOP 低开销 | 后续根据 Production SLO 扩展少量稳定事件 |
| Micrometer Adapter | **Current** | Client/Server latency、retry、connection、heartbeat、Registry、TLS/reload 指标；避免 endpoint/instanceId/error-message/traceId 标签 | 后续提供 Grafana Dashboard 模板 |
| OpenTelemetry Adapter | **Current** | Consumer/Provider Span + W3C traceparent/tracestate/baggage；真实 Peach RPC E2E 验证跨 wire parent-child | 后续补更多 semantic conventions |
| JFR Adapter | **Current** | 慢/失败 RPC、reconnect、heartbeat、Registry recovery、TLS/reload 低频事件 | 后续结合线上 profiling 指南 |
| 运行时诊断基线 | **Current** | Metrics + distributed tracing + JFR 三层诊断能力均已具备独立 Adapter | 后续完善 SLO/告警/Dashboard |

### 3.8 Wire Compatibility / Codec

| 能力 | 状态 | 当前实现 | 生产缺口 / 下一步 |
|---|---|---|---|
| 固定 Protocol Header | **Current** | v1 32 字节固定 Header | 增加跨版本 compatibility matrix |
| Codec/Compression Wire ID | **Current** | ID 已固化并保留 | 不能按 Classpath/SPI 顺序动态分配 |
| Fory 默认 Codec | **Current** | 当前高性能主 Codec | Stable Type ID 尚未完成 |
| Stable Type ID | **Proposed** | 尚未实现 | 定义确定性 ID 与冲突检测 |
| Schema fingerprint | **Proposed** | 尚未实现 | 支持版本/schema 不一致快速检测 |
| Rolling upgrade compatibility | **Proposed** | 尚未形成正式策略 | 明确 N/N+1 双向兼容、失败模式与回滚 |
| Protobuf / IDL | **Future** | Wire ID 已预留 | 在兼容模型稳定后再实现跨语言路径 |
| Kryo / Hessian2 / JSON | **Future** | Wire ID/规划存在 | 不阻塞近期生产主线 |

### 3.9 协议 Robustness 与测试

| 能力 | 状态 | 当前实现 | 生产缺口 / 下一步 |
|---|---|---|---|
| Header/length/handshake 基础校验 | **Current** | 已有正常与部分异常路径测试 | 增加系统性 malformed/fuzz matrix |
| CANCEL/Drain/Retry/Circuit 单元与 Transport 测试 | **Current** | 已覆盖核心行为 | 增加并发竞态与长时间稳定性 |
| Etcd 真实集成测试 | **Current** | register/watch/namespace/lease/recovery/compaction/restart；3 节点 leader transfer 在独立 Chaos workflow 验证 | 继续补网络黑洞/partition 与 soak |
| Nacos 真实集成测试 | **Current** | register/query/subscribe/unregister + RPC round-trip；独立 JVM E2E 覆盖 Nacos restart、Provider re-registration、Consumer re-subscribe | 继续补 auth-enabled 与网络分区 |
| 独立进程 RPC E2E | **Current** | CI 真正启动 Provider/Consumer executable JAR；覆盖 Provider restart、同一 Consumer 恢复、Nacos restart、新 Consumer 发现恢复、Provider 迁移端口后的 subscription redo | 增加滚动多实例与长时间 soak |
| Fuzz / property testing | **Proposed** | 尚未系统建立 | 覆盖长度溢出、截断、未知类型、重复帧、慢帧等 |
| Soak test | **Partial** | 已有 10k Virtual Thread soak、CI 短时 smoke、30m controlled evidence gate 与环境指纹 | 固定硬件完成长时间运行、内存/GC/连接稳定性基线 |
| Chaos test | **Partial** | 已有 Etcd 3 节点 leader-transfer Chaos workflow 与 Nacos restart process E2E | 仍缺网络黑洞/分区、长时间 soak 与更大规模并发故障矩阵 |

### 3.10 运维与发布

| 能力 | 状态 | 当前实现 | 生产缺口 / 下一步 |
|---|---|---|---|
| Maven Reactor / CI | **Current** | JDK 21 + `check_project.py` + `clean verify -Pquality` | 继续作为所有 PR 基础门禁 |
| Examples | **Current** | API/Provider/Consumer 分模块；CI 运行独立 executable JAR recovery E2E 与 Nacos restart | 继续补多实例滚动发布示例 |
| Capacity Planning | **Partial** | 已有 `capacity-planning.md` 方法论、证据分层、QPS/Core 与实例数计算模板 | 固定硬件填充生产数值，并形成 connections/maxInflight/maxConcurrent/CPU pool/timeout/retry 推荐区间 |
| Upgrade Guide | **Proposed** | 尚未完成 | 协议、Codec、Registry、Starter 升级步骤 |
| Rollback Guide | **Proposed** | 尚未完成 | N/N+1 回滚与 Registry/Codec 兼容边界 |
| Compatibility Matrix | **Proposed** | 尚未形成 | JDK/Spring/Protocol/Codec/Adapter 版本矩阵 |
| Production SLO | **Proposed** | 尚未定义 | 结合固定硬件和业务模型定义可验证目标 |

### 3.11 Streaming

| 能力 | 状态 | 说明 |
|---|---|---|
| Unary RPC | **Current** | 当前主线能力 |
| Server Streaming | **Optional** | 未实现 |
| Client Streaming | **Optional** | 未实现 |
| Bidirectional Streaming | **Optional** | 未实现 |
| Stream flow control/backpressure | **Optional** | 若 Streaming 进入正式范围，必须独立设计 window、cancel、half-close、buffer limit |

Streaming 当前**不作为近期 Production GA 的必选门禁**。如果项目后续明确需要流式场景，则必须单独建立协议与背压设计，不应直接复用 Unary 模型。

---

## 4. 生产路线

### 4.1 依赖关系

~~~mermaid
flowchart LR
    C1[V2-C.1<br/>Annotation Runtime + Nacos] --> C2[V2-C.2<br/>Connection & Control-plane HA]
    C2 --> C3[V2-C.3<br/>Security & Observability]
    C3 --> D[V2-D<br/>Performance Kernel Second Pass]
    D --> E[V2-E<br/>Wire Compatibility & Strategic Ecosystem]
    E --> GA[Production GA Gate]
~~~

路线原则：

> **优先补齐高可用恢复、安全和可观测性，再做第二轮极限性能优化，最后扩展更多生态。**

不建议在 V2-C.2/V2-C.3 完成前优先增加大量 Registry/Codec Adapter。

---

## 5. V2-C.1：注解运行时与 Nacos 控制面

**状态：Current（当前分支）**

已完成：

- `@PeachRpcService` / `@PeachRpcReference` 驱动按需运行时；
- Client/Server capability guard；
- Provider advertised endpoint；
- Examples API/Provider/Consumer 拆分；
- Nacos 3.2.4 Adapter；
- Nacos 有界控制面执行器；
- 真实 Nacos 集成与 RPC round-trip；
- Starter / docs / CI 同步。

该阶段解决“怎么自然接入业务”和“怎么扩展第二个生产 Registry”，不代表连接级 HA 已完成。

---

## 6. V2-C.2：连接与控制面 HA 闭环

**状态：Current（当前分支）**

### 6.1 已完成

1. PING/PONG heartbeat 与 Client/Server idle detection；
2. heartbeat timeout 后 half-open/silent connection 摘除；
3. request-driven single-flight reconnect + exponential backoff + full jitter；
4. Consumer 逻辑 Deadline 覆盖 reconnect/connect/HELLO/ACK/request；
5. absolute deadline + relative timeout budget 滚动兼容，并在真实 Socket write 前刷新相对预算；
6. connection lifecycle RpcObserver；
7. Etcd compaction、restart 与 3 节点 leader transfer；
8. Nacos restart、Provider re-registration、Consumer re-subscribe；
9. last-known-good 数据面与独立 JVM recovery E2E；
10. 完整 Reactor、质量门禁与独立 Chaos workflow。

### 6.2 后续增强（不再阻塞 V2-C.2 Current）

1. Etcd/Nacos 网络黑洞、partition 与更长时间 soak；
2. 多 Provider 滚动发布/恢复矩阵；
3. Registry 专属恢复 Observer 与标准指标映射放入 V2-C.3；
4. auth-enabled Nacos 与凭据错误脱敏放入 V2-C.3。

### 6.3 验收标准

- 静默断链能在明确时间界限内被 Heartbeat 检测；
- 大量连接同时断开时不会同步重连形成 thundering herd；
- Provider/Consumer 在 Registry 短暂不可用期间数据面不因为一次控制面错误立即清空可用目录；
- Etcd compaction 后能够重新 Range 并从有效 revision 继续 Watch；
- Nacos 重启后 Provider 能恢复注册，Consumer 能恢复订阅；
- reconnect/recovery 具有有界退避，并通过 Core `RpcObserver` 暴露连接生命周期事件；标准 metrics/tracing Adapter 属于 V2-C.3；
- 独立进程 E2E 覆盖启动、调用、Provider 停止、Consumer 失效感知、Provider 恢复。

---

## 7. V2-C.3：安全与可观测性

**状态：Current**

### 7.1 已实现

1. TLS / mTLS / CA / Hostname Verification；
2. X.509 有效期校验与 PEM 在线 Reload；
3. Registry/TLS/Certificate Observer；
4. Micrometer Adapter；
5. OpenTelemetry CLIENT/SERVER Span 与 W3C Metadata propagation；
6. JFR Adapter；
7. Registry Credential 配置边界与脱敏；
8. TLS + Heartbeat + reconnect 组合测试；
9. 真实 RPC OTel Trace E2E。

### 7.2 后续增强（不阻塞 V2-C.3 Current）

1. auth-enabled Nacos Server 独立集成环境；
2. Grafana Dashboard / Alert 模板；
3. PKCS#12/JKS；
4. 更大规模 TLS 性能与证书轮换 soak。

### 7.3 标准指标

至少覆盖：

- RPC request count / latency；
- p50/p99/p99.9 的外部采集基础；
- client/server inflight；
- retry / timeout / cancel；
- circuit open / half-open probe；
- outlier ejection；
- provider overloaded；
- active connection / reconnect；
- heartbeat failure；
- Registry control-plane failure/recovery；
- TLS handshake failure。

### 7.4 验收标准

- Core 仍不直接依赖 Micrometer/OpenTelemetry/JFR；
- Adapter 缺失时数据面开销保持当前 NOOP 语义；
- TLS/mTLS 不绕过现有 HELLO/ACK 与 handshake timeout 边界；
- 证书错误、过期、轮换均有自动化验证；
- 观测 Adapter 自身异常不能破坏 RPC 数据面。

---

## 8. V2-D：性能内核第二次升级

**状态：In Progress**

该阶段必须遵循：

> **Benchmark first, API change second。**

### 8.1 第一批已进入实现

V2-D 第一批已经进入主线：

1. Protocol encode/decode payload 参数化到 64B / 256B / 1KiB / 16KiB / 1MiB；
2. 新增完整 Peach RPC byte[] E2E benchmark，支持 connection shard 1 / 2 / 4 / 8；
3. 并发矩阵通过 JMH `-t` 控制，10k 并发保留给异步/虚拟线程 soak harness；
4. 新增固定 Header Request ID 无对象 accessor；
5. Client response routing 移除临时 `ByteBuffer`；
6. Server 正常 REQUEST tracking 移除完整 `RpcFrame.decode` 及其 Metadata/Payload copy；
7. timeout budget 原地刷新不再创建 `RpcFrameView`；
8. FrameAccumulator 增加 complete/fragmented benchmark，并复用固定 32B Header 数组；
9. v1 wire、Fory Codec ID 1 payload、TLS/Trace/Registry 语义保持不变。

### 8.2 V2-D.2：Performance Evidence & 10k Soak

当前 V2-D.2 已实现：

1. 完整 payload × connection shard × concurrency JMH Matrix Runner；
2. NOOP / CPU / BLOCKING / SLOW_PROVIDER / OVERLOAD execution/fault Matrix；
3. sample + throughput 双模式；
4. `-prof gc` allocation / GC profiling；
5. JMH JSON -> CSV / Markdown 聚合，并保留 overload success/error AuxCounters；
6. JDK 21 Virtual Thread 10k logical-concurrency soak；
7. Soak 输出吞吐、p50/p99/p99.9、错误率、Heap、GC、CPU、inflight、连接与错误类型；
8. 普通 CI 增加 Matrix smoke 与 10k short soak smoke；
9. 独立 Performance Evidence workflow 支持 full matrix 与长时间 10k soak Artifact；
10. TLS/PLAINTEXT security matrix tooling；
11. Retry Budget / Circuit Breaker / Outlier Ejection resilience primitive matrix tooling；
12. `capacity-planning.md` 容量规划方法论与数据记录模板；
13. fixed Runner controlled evidence gate、硬件环境指纹与证据完整性校验；
14. `decision-inputs.json/.md` 自动生成，为 Buffer ownership / Future/PendingRequest / Capacity Planning 提供描述性决策输入；
15. `compare_v2d2_evidence.py` 支持至少 3 次 controlled run 的环境一致性、Matrix shape、CV/spread 重复性分析；
16. `promote_v2d2_baseline.py` 仅允许 repeatability PASS 生成可审计的 baseline candidate，不自动晋级 Production SLO。

当前仍未把共享 CI Runner 上的数值写成 Production SLO。固定硬件 full matrix、至少 30 分钟 10k soak、TLS/PLAINTEXT 数值对比、Provider fault 与 resilience 数值基线仍属于 V2-D.2 未完成证据。

详细运行方式见 [V2-D.2 Performance Evidence & 10k Soak](performance-evidence-v2d2.md)，容量计算与生产记录模板见 [Capacity Planning Guide](capacity-planning.md)。

#### V2-D.2-E1：Controlled Evidence Execution

**状态：In Progress / Engineering Ready / Real Runs Pending**

当前分支已补齐固定 Runner preflight、expected/actual GitHub Runner 名称校验、hashed physical-host fingerprint、环境 baseline、唯一 Run ID、Evidence SHA-256 Manifest、至少三次 controlled run 编排，以及 GitHub self-hosted Evidence workflow。即使不同机器拥有相同硬件规格，也会因 host fingerprint 不一致而被拒绝。E1 仍未完成：退出该阶段前必须在同一真实固定 Runner 上产生至少 3 份完整 Evidence Bundle，并且每份 Validation 都为 PASS。

#### 完整矩阵维度

Payload：

- 64B；
- 256B；
- 1KiB；
- 16KiB；
- 1MiB。

Concurrency：

- 1；
- 16；
- 64；
- 256；
- 1024；
- 10000。

Connection shard：

- 1；
- 2；
- 4；
- 8。

场景：

- no-op；
- CPU；
- blocking；
- slow Provider；
- slow Consumer；
- overload；
- retry/circuit/outlier fault injection。

采集：

- QPS/Core；
- p50/p99/p99.9；
- CPU；
- Allocation；
- GC；
- inflight；
- connection count；
- error rate。

### 8.3 根据数据决定的候选优化

- Buffer ownership / Buffer-oriented Codec；
- FrameAccumulator 减少完整 frame copy；
- Generated path 消除参数 `Object[]`；
- 降低 per-request CompletableFuture/PendingRequest 分配；
- EndpointStats / admission 共享原子结构优化；
- Compression 策略。

任何候选优化只有在基准证明其收益大于复杂度和安全成本后，才进入默认路径。

---

## 9. V2-E：Wire Compatibility 与战略生态

**状态：Proposed / Future**

### 9.1 必须优先完成的兼容能力

1. Fory Stable Type ID；
2. Type ID collision detection；
3. Schema fingerprint；
4. N/N+1 rolling compatibility；
5. rollback compatibility；
6. Protocol/Codec compatibility matrix。

### 9.2 战略生态

兼容模型稳定后，优先级建议：

1. Protobuf / IDL；
2. Kubernetes EndpointSlice；
3. 其他 Registry / Codec。

原因：

- Protobuf/IDL 能验证跨语言、稳定 Schema 与 Codegen 边界；
- Kubernetes EndpointSlice 能验证 discovery-only Registry Capability 模型；
- ZooKeeper、Consul、Eureka、Kryo、Hessian2、JSON 属于生态扩展，不应早于核心生产门禁。

---

## 10. Production GA Gate

Peach RPC 从 Preview 提升为 Production Ready 前，建议以下门禁全部满足。

### 10.1 High Availability

- [x] Heartbeat / idle detection；
- [x] reconnect backoff + jitter；
- [x] relative timeout budget；
- [x] Etcd compaction/restart/leader change；
- [x] Nacos restart/re-registration/re-subscribe；
- [x] 独立进程 Provider restart 与恢复 E2E；
- [ ] Etcd/Nacos 网络黑洞/partition 与长时间 Registry/Transport 恢复 soak test。

### 10.2 Security

- [x] TLS；
- [x] mTLS；
- [x] hostname/peer verification；
- [x] 证书过期与轮换；
- [ ] 凭据/证书错误脱敏。

### 10.3 Observability

- [x] Micrometer；
- [x] OpenTelemetry；
- [x] JFR；
- [x] 连接/Registry 生命周期指标；
- [ ] 统一错误与状态语义。

### 10.4 Performance

- [ ] 固定环境完整 benchmark matrix；
- [ ] 至少 3 次 controlled run 的 repeatability report；
- [ ] QPS/Core、p99/p99.9、Allocation、GC 基线；
- [ ] 固定硬件 overload / slow endpoint / resilience fault 数值基线；
- [ ] 是否进入 Buffer-oriented 默认路径有数据结论；
- [ ] 容量规划参数有可重复实验支撑。

### 10.5 Compatibility

- [ ] Fory Stable Type ID；
- [ ] Schema fingerprint；
- [ ] N/N+1 rolling upgrade；
- [ ] rollback；
- [ ] Protocol/Codec compatibility matrix。

### 10.6 Robustness

- [ ] malformed frame matrix；
- [ ] fuzz/property test；
- [ ] race/concurrency test；
- [ ] 长时间 soak test（10k soak 工具已具备，固定环境长跑证据未完成）；
- [ ] chaos test；
- [ ] Maven/CI/Javadoc 全门禁持续通过。

### 10.7 Operations

- [ ] Capacity Planning Guide（方法论已完成，固定硬件生产数值待填充）；
- [ ] Upgrade Guide；
- [ ] Rollback Guide；
- [ ] Compatibility Matrix；
- [ ] 生产推荐配置与安全默认值；
- [ ] 明确 SLO/告警建议。

---

## 11. 文档治理规则

从本文建立后：

1. **本文负责“现在有什么、还缺什么、下一步优先级”。**
2. 机器可读状态统一维护在 `capability-status.properties`，`check_project.py` 强制校验关键文档标记和已知过期表述。
3. `readiness.md` 负责解释“为什么当前还是 Preview、Production Gate 是什么”。
4. `performance.md` 负责性能测量方法、基准和优化证据。
5. `architecture.md` 负责当前架构与运行时流程。
6. V2-A/V2-B/V2-B.1/V2-B.2/V2-C.x 文档负责阶段设计和历史决策。
7. 每个改变能力状态的 PR 都必须同步更新本文对应矩阵项。
8. 不允许只在旧阶段文档中把能力写成“已完成”，而不更新本文。
9. 旧文档中若存在历史描述，不应静默改写历史；应增加“Current status 见 Production Roadmap”的链接。

这套规则的目标是避免：

~~~text
旧计划：Future
新代码：Implemented
Readiness：Partial
README：另一种表述
~~~

最终形成单一、可持续维护的生产能力视图。
