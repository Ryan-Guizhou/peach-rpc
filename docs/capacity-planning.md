# Peach RPC Capacity Planning Guide

> 状态：**Current Methodology / Production Numbers Pending Fixed-Hardware Evidence**  
> 适用阶段：V2-D.2  
> 证据入口：`docs/performance-evidence-v2d2.md`、`summary.csv`、10k soak JSON。

## 1. 目标

容量规划不是从“机器有多少核”反推 QPS，而是从同一业务形态、同一安全模式、同一 Payload 和同一延迟目标下的可重复证据推导。

正式容量结论至少需要：

- 固定 CPU/内存/OS/JDK；
- 固定 Peach RPC commit；
- 固定 TLS/PLAINTEXT；
- 固定 Payload 分布；
- 固定 connections-per-endpoint；
- 固定并发；
- JMH allocation/GC 结果；
- 10k soak 的吞吐、p50/p99/p99.9、CPU、heap、GC、error rate。

共享 GitHub Runner 的结果只能验证工具链，不能进入生产容量基线。

## 2. 证据分层

| 层级 | 输入 | 用途 |
|---|---|---|
| Micro | Protocol / FrameAccumulator / ResiliencePath JMH | 判断对象分配、原子操作和协议局部成本 |
| E2E | Payload × shard × concurrency × security JMH | 找到吞吐、尾延迟和 allocation/op 拐点 |
| Fault | Slow Provider / Overload / resilience matrix | 观察保护机制在故障态的额外成本 |
| Soak | 10k logical concurrency JSON | 观察长时间 CPU、heap、GC、错误率、连接与恢复稳定性 |

容量建议必须以 E2E + Soak 为主，Micro 只用于解释原因。

## 3. 核心计算

### 3.1 QPS/Core

固定环境下：

```text
qpsPerCore = throughputOpsPerSecond / processCpuCoresAverage
```

如果一次实验平均消耗的 CPU 核数接近 0，说明采样窗口或负载不足，该点不能用于容量规划。

### 3.2 目标 Core 数

```text
requiredCores =
    targetQps / validatedQpsPerCore * headroomFactor
```

`headroomFactor` 由业务 SLO、发布策略和故障冗余确定。本文不预设生产默认值；例如 1.25 只表示“额外保留 25% 余量”的计算示例，不是 Peach RPC 的推荐值。

### 3.3 实例数

```text
instances =
    ceil(requiredCores / allocatableCoresPerInstance)
```

还必须同时满足：

- p99/p99.9 目标；
- error-rate 目标；
- heap/GC 目标；
- 单连接 max inflight；
- Provider maxConcurrent；
- CPU execution pool / queue 容量；
- Registry 与故障域冗余。

因此实例数取 CPU、延迟、并发和故障冗余约束中的最大值。

## 4. Payload 与 TLS 必须分桶

至少分别维护：

- 64B / 256B；
- 1KiB；
- 16KiB；
- 1MiB；
- PLAINTEXT；
- TLS。

不能使用 256B PLAINTEXT 的 QPS/Core 去估算 1MiB TLS 流量。

业务实际是混合 Payload 时，应按线上分布加权，或直接构造与线上分布一致的专用 benchmark。

## 5. Connection Shard 决策

对每个 Payload/并发点比较 1/2/4/8 shards：

1. 吞吐是否继续上升；
2. p99/p99.9 是否下降；
3. CPU 是否明显增加；
4. allocation/op 是否恶化；
5. max observed connections 是否符合预期。

选择满足 SLO 的**最小** shard 数，而不是机械选择吞吐最高的 shard 数。更多连接会增加握手、心跳、文件描述符和故障恢复成本。

## 6. Provider 并发与队列

Provider 容量必须同时验证：

- `maxConcurrent`；
- CPU pool parallelism；
- CPU queue capacity；
- blocking virtual-thread workload；
- slow-provider workload；
- overload error rate。

当排队开始显著推高 p99/p99.9 时，应优先限制 admission 或增加实例，而不是无限扩大 queue。

## 7. 内存与 GC

JMH 重点观察：

- `gc.alloc.rate.norm`；
- `gc.alloc.rate`；
- GC count/time。

Soak 重点观察：

- heap used before/after；
- GC count/time delta；
- 长时间 error rate；
- platform-thread peak。

如果小 Payload 下 allocation/op 仍高，应优先检查 Future/PendingRequest/timer/callback；如果大 Payload 随 Payload 近似线性增长，应优先检查 byte[] copy、FrameAccumulator 与 Buffer ownership。

## 8. Buffer Ownership / Future / PendingRequest 决策门

这些优化只有满足以下条件才进入默认路径：

### Buffer ownership

- 大 Payload 的 allocation/op 或 GC 明显成为主要瓶颈；
- p99/p99.9 与 copy/GC 有可重复相关性；
- ownership API 不破坏异常、取消、TLS 和 backpressure 语义；
- 优化收益在至少两个固定环境复现。

### Future / PendingRequest

- 小 Payload 下 allocation/op 主要由 per-request Future/PendingRequest 主导；
- CPU/吞吐或尾延迟收益可重复；
- cancellation、timeout、retry、circuit、reconnect 行为全部回归通过。

没有证据时保持当前实现，不以“理论零分配”作为改造理由。

## 9. 发布容量记录模板

每次形成生产建议时保存：

```text
commit:
machine:
cpu model:
physical/logical cores:
memory:
os:
jdk:
jvm flags:
security mode:
payload distribution:
connections per endpoint:
concurrency:
provider maxConcurrent:
warmup:
measurement:
qps:
qps/core:
p50:
p99:
p99.9:
allocation/op:
gc count/time:
process cpu cores average:
heap before/after:
error rate:
recommended headroom:
recommended cores:
recommended instances:
known limits:
```

## 10. 当前未关闭项

当前 V2-D.2 仍需固定硬件完成：

- full matrix；
- 至少 30 分钟 10k soak；
- TLS/PLAINTEXT 数值对比；
- retry/circuit/outlier 与 Provider fault 数值基线；
- QPS/Core、p99/p99.9、allocation/op 基线；
- Buffer ownership 与 Future/PendingRequest 的最终数据决策。

因此本文当前提供的是**可执行容量规划方法**，不是 Production GA 数值承诺。
