# 实施路线

> 本文保留阶段实施历史。当前生产能力状态、未完成项和后续优先级统一见 [Production Roadmap / Capability Matrix](production-roadmap.md)。

## Phase 1：早期工程收敛

- 模块从早期 17 个概念粒度模块收敛；当前 Reactor 已演进为 12 个具备真实依赖隔离价值的顶层模块。
- 完成 Spring Boot Starter/AutoConfiguration。
- 文档改为中文主导，README 中英文切换。
- Maven 版本和插件统一管理。
- CI 执行 JDK 21、项目检查、`clean verify -Pquality`。

## Phase 2：热路径优化

- APT 生成 Client Stub 与 Server Dispatcher。
- Event Loop 亲和连接组与多连接策略。
- Codec Buffer 化，减少中间 `byte[]` 分配。
- 完善 JMH 与端到端 benchmark。

## Phase 3：生产治理

已完成第一批：

- Retry Budget、Cancel、Circuit Breaker、Outlier Ejection。
- Graceful Drain。
- Raw Vert.x / 完整 RPC 端到端延迟基线。

第二批已完成：

- Provider execution policy：BLOCKING_VIRTUAL / CPU / guarded DIRECT。
- RpcObserver 低依赖可观测性基础契约。
- 真实 Etcd 注册、Watch、namespace、Lease 集成测试。

后续：

- TLS/mTLS。
- Micrometer/OpenTelemetry/JFR Adapter。
- Buffer ownership / Buffer-oriented Codec。
- Fory 稳定 Type ID / Schema fingerprint。
- Etcd compaction、断链恢复专项故障测试。


## Phase 3.2：V2-B.2 高可用收口（已完成）

已完成：

- 将 V2-B.1 第二批能力重新基于 main 整合；
- Etcd Lease 丢失后的 active registration 自动恢复；
- Etcd Watch/recovery 使用指数退避 + jitter；
- Provider 控制面注册/注销等待增加超时边界；
- `@PeachRpcService` 成为 Spring stereotype；
- examples 完整 Spring Boot RPC round-trip smoke test；
- examples 可执行 JAR 与独立运行文档。

下一批优先：

1. PING/PONG heartbeat + idle detection；
2. 相对 timeout budget，降低跨节点 wall-clock 偏差；
3. Micrometer/OpenTelemetry/JFR Adapter；
4. Etcd compaction / disconnect / restart / leader-change 故障注入；
5. TLS/mTLS。


## 下一阶段

后续不再在本文件重复维护详细状态。V2-C.1、V2-C.2、V2-C.3、V2-D、V2-E 与 Production GA Gate 统一见 [Production Roadmap / Capability Matrix](production-roadmap.md)。
