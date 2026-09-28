# 生产就绪门禁

当前版本定位为 Preview，可用于内部验证和中型项目集成试点，但不直接宣称生产就绪。

## V2-B.1 第一批已完成

- Consumer timeout 与主动 Future cancel 可传播为 CANCEL，Provider 会取消 connection-local 请求并中断对应虚拟线程任务；
- 自动 Retry 仅对显式 `@PeachRpcIdempotent` 方法生效，并受 Retry Budget、最大 attempt、整体 Deadline 与随机退避约束；
- Endpoint 连续基础设施失败支持临时 Outlier Ejection，默认 P2C/EWMA 跳过被剔除实例；
- 方法级 Circuit Breaker 支持连续失败 OPEN 与单探针 HALF_OPEN；
- Provider 关闭前先注销 Registry，再通过 GO_AWAY 进入 Graceful Drain，等待 inflight 完成或 drain timeout；
- 新增 Raw Vert.x 与完整 Peach RPC 的端到端延迟基线，用于观察 RPC Added Latency；
- Cancellation、Drain、Retry Budget、Circuit Breaker、Outlier 路径已有自动化测试。

## V2-B.1 第二批已完成

- Provider execution policy：默认 BLOCKING_VIRTUAL，CPU 使用有界线程池，DIRECT 默认关闭并需要显式允许；
- Core 增加无第三方观测依赖的 RpcObserver，并覆盖 Client attempt/retry 与 Provider invocation 生命周期；
- Starter 自动组合 RpcObserver Bean，并暴露 Provider execution 配置；
- Etcd Adapter 增加真实 Etcd 集成测试，覆盖注册/注销、Watch 快照、namespace 隔离和 Lease 过期。

## 仍需完成的生产门禁

正式成为中型项目默认 RPC 层之前，至少还需要：

- 更完整的协议兼容、畸形帧和故障注入测试；
- Etcd compaction、断链重连等更强故障注入测试；
- TLS/mTLS 与证书生命周期；
- Micrometer、OpenTelemetry/Tracing、JFR 具体 Adapter；
- Buffer ownership / buffer-oriented Codec，是否进入默认路径必须由基准收益决定；
- Fory 稳定 Type ID、Schema fingerprint、冲突检测与滚动升级兼容策略；
- 多 payload、多并发、过载、慢 Consumer/Provider 的稳定端到端性能基线；
- 容量规划与升级/回滚文档；
- Streaming RPC 若进入项目范围，需要单独完成流控、取消与背压设计。

当前已经具备的基础约束包括长连接、多路复用、有界 inflight、Provider 并发准入、本地服务目录、SPI 边界、协议长度校验、真实握手、取消传播、受预算重试、端点剔除、方法熔断和优雅排空。
