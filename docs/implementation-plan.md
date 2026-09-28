# 实施路线

## Phase 1：当前 PR

- 模块从 17 个收敛到 9 个。
- 完成 Spring Boot Starter/AutoConfiguration。
- 文档改为中文主导，README 中英文切换。
- Maven 版本和插件统一管理。
- CI 执行 JDK 21、项目检查、`clean verify -Pquality`。

## Phase 2：热路径继续优化

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


## Phase 3.2：V2-B.2 高可用收口

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
