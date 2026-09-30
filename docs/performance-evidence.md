# Peach RPC 性能证据与容量验证

> 状态：**Tooling Current / Environment-specific Evidence Required for Numeric Claims**

## 1. 原则

Peach RPC 区分：

1. **性能工具是否可用**；
2. **某个固定环境的结果是否可重复**；
3. **结果是否可以晋级为容量建议或官方性能声明**。

GitHub shared runner 只用于验证工具和行为，不作为生产 SLO 来源。

## 2. 工具

仓库提供：

- JMH 参数化 Benchmark；
- payload / concurrency / connection-shard matrix；
- sample + throughput；
- `-prof gc`；
- Provider execution/fault scenario；
- TLS/PLAINTEXT security matrix；
- Retry/Circuit/Outlier resilience matrix；
- 10k logical-concurrency Virtual Thread soak；
- Evidence validator；
- repeatability compare；
- baseline candidate；
- before/after regression closure。

## 3. Evidence 流程

```mermaid
flowchart LR
    Preflight[Fixed Runner Preflight]
    Runs[>= 3 Controlled Runs]
    Validate[Evidence Validation]
    Repeat[Repeatability]
    Candidate[Baseline Candidate]
    Review[Engineering Review]
    Regression[Before/After Regression]
    Profile[Environment Profile]

    Preflight --> Runs --> Validate --> Repeat --> Candidate --> Review --> Regression --> Profile
```

## 4. 固定环境要求

至少记录：

- Commit SHA；
- JDK；
- JVM flags；
- OS/Kernel；
- CPU；
- Memory；
- Runner identity；
- physical host fingerprint；
- payload；
- concurrency；
- connection shard；
- execution/fault scenario。

不同 physical host 的结果不能自动合并为同一受控基线。

## 5. 指标

- throughput；
- p50/p99/p99.9；
- QPS/Core；
- allocation/op；
- GC；
- CPU；
- heap；
- inflight；
- connection count；
- error rate；
- overload success/error；
- failure category。

## 6. 10k Soak

10k 指 logical concurrency，不等于创建 10k TCP 连接。

正式 soak 应在目标硬件运行足够时间，至少记录：

- warmup；
- duration；
- concurrency；
- payload；
- connections；
- client timeout；
- heap/GC/CPU；
- p99/p99.9；
- error breakdown。

## 7. 性能优化门

以下候选只有 Evidence 证明收益后才进入默认路径：

- Buffer-oriented Codec；
- FrameAccumulator copy reduction；
- Object[] elimination；
- Future/PendingRequest allocation reduction；
- EndpointStats/admission contention 优化；
- Compression。

## 8. GA 边界

1.0.0 可以在没有厂商级固定硬件数字的情况下发布，因为：

- 正确性、兼容性、Chaos、Release Gate 独立可验证；
- 项目不发布未经验证的官方 QPS/SLO；
- 每个使用者的 CPU、Payload、业务方法、TLS、Registry 和 JVM 都不同。

但任何官方性能比较、生产容量推荐、QPS/Core 或 p99/p99.9 数字，都必须附带本流程产生的 Evidence。

## 9. 相关文档

- [性能指南](performance.md)
- [容量规划](capacity-planning.md)
- [生产配置](production-configuration.md)
- [发布就绪](release-readiness.md)
