# Peach RPC 性能指南

> 状态：**GA Tooling Current / Numeric Claims Require Controlled Evidence**

## 1. 性能设计原则

Peach RPC 不以“理论零分配”为目标，而以可重复证据驱动：

- throughput；
- p50/p99/p99.9；
- allocation；
- GC；
- CPU；
- error rate；
- overload/resilience behavior。

## 2. 已应用的热路径原则

- Consumer 热路径不访问 Registry；
- 方法 Codec 在启动期绑定；
- Generated Stub/Dispatcher 优先；
- Request ID 与 pending table connection-local；
- P2C/EWMA 直接读取本地数组快照；
- 接收端使用 `RpcFrameView` 避免不必要 Metadata/Payload copy；
- timeout budget 原地更新；
- 正常高频路径 Observer 可为 NOOP；
- Provider 对业务 `CompletionStage` 使用完成回调，不让未完成的异步结果长期占用 CPU/Virtual Thread worker；Stage 稍后完成时，响应编码重新调度到 Provider 管理的执行资源，避免占用外部 Future completion thread / EventLoop；
- Retry Budget 在额度已满时只读检查，不再执行无意义的 CAS 写回；Circuit Breaker 在健康 CLOSED 状态的成功路径避免重复原子写。

## 2.1 Consumer 响应完成隔离（PR-A）

Consumer 的 Transport 响应完成不直接执行 Fory 解码和业务 Future continuation，而是提交到独立有界执行器。默认线程数为 `max(2, min(16, availableProcessors))`，队列大小 4096，可以通过 `PeachRpcClient.Builder` 调整。队列饱和快速返回 `OVERLOADED`；拒绝处理不得回退到 EventLoop 上执行用户回调。

线程切换可能影响轻量报文的 p99，必须通过固定环境性能对比来确认；当前不能宣称没有回归。

## 3. 当前仍存在的分配

- `CompletableFuture` / PendingRequest；
- Fory 参数 Object[]；
- 完整 frame byte[]；
- 部分 timer/callback。

这些不是自动等于 Bug。只有 Evidence 显示它们成为主要瓶颈时才进入优化。

## 4. Benchmark

Benchmark 模块：

```text
peach-rpc-benchmarks
```

覆盖 payload、concurrency、connection shard、execution/fault scenario。

普通 CI 只运行 smoke；正式性能比较应使用固定硬件。

## 5. 10k logical concurrency

仓库提供 Virtual Thread soak harness。10k 表示逻辑并发，不代表 10k TCP connection。

CI 使用短时间 smoke 验证工具可运行；长时间结果应在目标环境采集。

## 6. 性能证据

完整 Evidence 流程见 [性能证据与容量验证](performance-evidence.md)。

## 7. 容量规划

见 [Capacity Planning](capacity-planning.md)。

必须同时考虑：

- payload；
- concurrency；
- QPS/Core；
- connection shard；
- Provider execution mode；
- TLS；
- Registry recovery；
- retry amplification；
- heap/native memory；
- GC。

## 8. 官方性能声明规则

没有固定环境 Evidence 时，项目不声明：

- “可达到 X QPS”；
- “p99 固定为 X ms”；
- “比某框架快 X%”；
- “建议固定线程数/连接数”。

Benchmark 工具可用于使用者自己的环境验证。
