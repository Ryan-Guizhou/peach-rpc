# OTRYX RPC 性能指南

> 状态：**GA Tooling Current / Numeric Claims Require Controlled Evidence**

## 1. 性能设计原则

OTRYX RPC 不以“理论零分配”为目标，而以可重复证据驱动：

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

Consumer 的 Transport 响应完成不直接执行 Fory 解码和业务 Future continuation，而是提交到独立有界执行器。默认线程数为 `max(2, min(16, availableProcessors))`，队列大小 4096，可以通过 `OtryxRpcClient.Builder` 调整。队列饱和快速返回 `OVERLOADED`；拒绝处理不得回退到 EventLoop 上执行用户回调。

线程切换可能影响轻量报文的 p99，必须通过固定环境性能对比来确认；当前不能宣称没有回归。

## 3. 当前仍存在的分配

- `CompletableFuture` / PendingRequest；
- Fory 1～N 个参数调用使用的 Object[]（兼容 Wire v1）；
- Fory 零参数路径共享不可变空数组，避免每次创建临时 Object[0]；
- 完整 frame byte[]；
- 部分 timer/callback。

这些不是自动等于 Bug。只有 Evidence 显示它们成为主要瓶颈时才进入优化。

## 3.1 已实现：帧重组热路径优化

`FrameAccumulator` 在无尾帧的完整帧路径绕过中间 `pending.appendBuffer`，仍保持独立 `byte[]` 的 Core 所有权边界；分片输入继续采用原有重组逻辑。独立基线与候选 Worktree 通过 JMH `-prof gc` 比较 B/op 与 sample p99，并上传原始 JSON。详见 [Frame Allocation Profiling 方案](reference/transport-allocation-profiling.md)。共享 CI 结果仅作 Smoke，不能替代受控端到端 p99/吞吐复核。

## 3.2 Fory 零参数分配优化与定向基准

`ForyRpcCodec.ForyMethodCodec.encode0()` 直接序列化共享的不可变空 `Object[]`，`encodeArguments(null)` 也复用同一空数组。仍然编码标准 Fory `Object[]` Payload，没有改变 Codec ID、Stable Type ID 或 Wire v1。单参数至四参数调用**仍使用 Object[]**，不作未经验证的无数组承诺。

`ForyRpcCodecTest.zeroArgumentFastPathMustPreserveHistoricalForyBytes` 对新旧路径逐字节比较，并验证反序列化及 `null` 参数兼容。新增 `ForyArgumentEncodingBenchmark`，分别比较零参数快路径、旧式零参数临时数组、单参数和四参数，支持 JMH `-prof gc` 的 `gc.alloc.rate.norm`。

从仓库根目录构建并运行：

```bash
mvn -B -ntp -pl otryx-codegen -am -DskipTests install
mvn -B -ntp -pl otryx-benchmarks -am -DskipTests package
bash scripts/run_fory_argument_allocation.sh
```

原始单版本证据位于 `target/fory-argument-allocation`。历史 Base 与 Candidate 的自动 A/B 多轮比较命令、强制来源校验、变异系数和 `REPORT_ONLY` 规则见 [性能证据与容量验证](reference/performance-evidence.md#10-fory-参数编解码-ab-分配量测量)。共享 CI 数据只属于 Smoke，不得用于生产性能或对比声明。

## 4. Benchmark

Benchmark 模块：

```text
otryx-benchmarks
```

覆盖 payload、concurrency、connection shard、execution/fault scenario。

普通 CI 只运行 smoke；正式性能比较应使用固定硬件。

## 5. 10k logical concurrency

仓库提供 Virtual Thread soak harness。10k 表示逻辑并发，不代表 10k TCP connection。

CI 使用短时间 smoke 验证工具可运行；长时间结果应在目标环境采集。

## 6. 性能证据

完整 Evidence 流程见 [性能证据与容量验证](reference/performance-evidence.md)。

## 7. 容量规划

见 [Capacity Planning](reference/capacity-planning.md)。

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
