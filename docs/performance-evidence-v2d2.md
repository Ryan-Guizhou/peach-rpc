# Peach RPC V2-D.2 Performance Evidence & 10k Soak

<!-- capability-status:v2-d=in-progress -->
<!-- capability-status:v2-d2=in-progress -->

> 状态：**In Progress / Tooling Implemented**  
> 基线：`main@f87dd0ef3bcc3e6faeb11e124750e28cdd547531`  
> 目标：建立可重复的完整性能矩阵、allocation/GC 证据链和 10k logical-concurrency soak。  
> 说明：本阶段先建立**测量能力和运行门禁**，不会把共享 CI Runner 上的数字直接声明为 Production SLO。

## 1. 为什么需要 V2-D.2

V2-D 第一批已经减少了几处确定性热路径分配：

- Client response routing 不再创建临时 `ByteBuffer`；
- Server request tracking 不再执行完整 `RpcFrame.decode()`；
- timeout budget 原地刷新不再创建 `RpcFrameView`；
- FrameAccumulator 复用固定 32B Header 数组。

下一步不能继续凭代码直觉重构 `CompletableFuture`、`PendingRequest`、Buffer ownership 或 Fory 参数对象图。必须先回答：

1. 每次 RPC 的 allocation/op 主要来自哪里；
2. 64B 与 1MiB Payload 的瓶颈是否相同；
3. 连接分片在不同并发下何时改善、何时恶化；
4. p99 / p99.9 在什么并发和 Payload 下开始失控；
5. 10k logical concurrency 下是否出现 GC、超时、错误、连接抖动或资源失控。

## 2. Evidence Pipeline

~~~mermaid
flowchart LR
    Code[Peach RPC Commit] --> Build[Benchmark JAR]
    Build --> Matrix[JMH Matrix]
    Build --> Soak[Virtual Thread Soak]
    Matrix --> JMHJSON[JMH JSON + GC Profiler]
    Soak --> SoakJSON[Soak JSON]
    JMHJSON --> Summary[CSV + Markdown Summary]
    SoakJSON --> Evidence[Evidence Artifact]
    Summary --> Evidence
    Evidence --> Decision[Optimization Decision]
~~~

核心规则：

> **代码优化必须由可重复证据驱动；工具可在 CI 验证，性能结论必须来自固定环境。**

## 3. JMH 完整性能矩阵

入口：

~~~bash
bash scripts/run_v2d2_benchmark_matrix.sh <profile> <output-directory>
~~~

可用 Profile：

| Profile | Payload | Connections | Threads | Mode | 用途 |
|---|---|---|---|---|---|
| `smoke` | 256B | 1 | 1 | sample | PR/CI 工具链验证 |
| `standard` | 64B / 1KiB / 16KiB | 1 / 4 | 1 / 64 / 256 | sample + thrpt | 日常性能回归 |
| `full` | 64B / 256B / 1KiB / 16KiB / 1MiB | 1 / 2 / 4 / 8 | 1 / 16 / 64 / 256 / 1024 | sample + thrpt | V2-D.2 完整证据 |

每个组合都会启用：

~~~text
-prof gc
-rf json
~~~

容量点发生 RPC/进程级失败时，standard/full profile 会把组合记录到 `failures.csv` 并继续执行；smoke profile 仍立即失败，避免工具链故障被吞掉。

因此可以同时获得：

- latency distribution；
- throughput；
- `gc.alloc.rate.norm`；
- `gc.alloc.rate`；
- GC count/time。

### 3.1 结果聚合

矩阵脚本结束后自动执行：

~~~bash
python3 scripts/summarize_v2d2_results.py <result-directory>
~~~

输出：

~~~text
environment.properties
*.json
summary.csv
summary.md
~~~

`summary.csv` 是后续容量分析和差异分析的机器可读入口。

## 4. Allocation Profiling

JMH 通过 `-prof gc` 记录：

- allocation bytes/op；
- allocation MB/s；
- GC count；
- GC time。

这一级数据用于回答：

~~~text
小 Payload allocation 高
        |
        +--> Future / PendingRequest / timer / callback

大 Payload allocation 高
        |
        +--> byte[] copy / FrameAccumulator / Buffer ownership / Codec
~~~

如果后续需要确认具体分配栈，再在固定环境补：

- JFR allocation profiling；
- async-profiler allocation；
- JDK Flight Recording。

这些更重的 profiler 不放入普通 PR CI。

## 5. 10k Logical-Concurrency Soak

入口：

~~~bash
bash scripts/run_v2d2_soak.sh
~~~

默认：

~~~text
concurrency              = 10000
payload                  = 256B
connections-per-endpoint = 4
warmup                   = 15s
measurement              = 600s
client-timeout           = 5s
driver                   = JDK 21 Virtual Threads
~~~

环境变量可覆盖：

~~~bash
PEACH_RPC_SOAK_CONCURRENCY=10000
PEACH_RPC_SOAK_PAYLOAD_SIZE=256
PEACH_RPC_SOAK_CONNECTIONS=4
PEACH_RPC_SOAK_WARMUP_SECONDS=30
PEACH_RPC_SOAK_DURATION_SECONDS=1800
PEACH_RPC_SOAK_CLIENT_TIMEOUT_MS=15000
PEACH_RPC_SOAK_OUTPUT=target/v2d2-soak.json
bash scripts/run_v2d2_soak.sh
~~~

### 5.1 为什么用 Virtual Threads

10k 并发验证的是：

> Peach RPC 在 10000 个 logical callers 下的数据面稳定性。

如果直接使用 10000 个 JMH/platform threads，Driver 本身的平台线程调度、栈内存和 context switch 会显著污染结果。

Soak Runner 使用 JDK 21 Virtual Threads 驱动同步 Generated Stub，使 Driver 的并发模型更接近高并发阻塞业务调用。

## 6. Soak 输出

`PerformanceSoakRunner` 输出 JSON，包含：

- commit；
- JDK / VM / OS / processors；
- JVM arguments；
- logical concurrency；
- payload bytes；
- connections per endpoint；
- successes / errors / error rate；
- throughput；
- 成功 RPC 的 p50 / p99 / p99.9 / max；
- max logical inflight；
- max observed connections；
- reconnect count；
- heartbeat timeout count；
- heap used before/after；
- GC count/time delta；
- average process CPU cores；
- peak platform thread count；
- error types。

Latency 使用固定上限的 Reservoir，避免长时间 soak 因保存全部请求样本而无限增长内存。

## 7. CI 与长期证据的边界

### 普通 CI

普通 CI 只验证：

1. Repository/document status checks；
2. Maven Reactor + `-Pquality`；
3. JMH matrix smoke；
4. **10000 logical-concurrency 的短时 soak smoke**；
5. Independent JVM / Nacos recovery E2E。

共享 Runner 上的 smoke：

- 只证明工具和高并发路径可以工作；
- 不作为生产性能阈值；
- 不因为 p99/QPS 波动直接阻塞 PR。

### Performance Evidence Workflow

`.github/workflows/performance-evidence.yml` 提供手工运行入口：

- `matrix_profile=smoke|standard|full`；
- 可独立开关 Matrix/Soak；
- Soak 默认 10000 concurrency / 600s；
- 最终上传 evidence artifact。

建议固定专用 Runner 后，把 full matrix + 长时间 soak 作为发布前证据门禁。

## 8. 正式性能结论的环境要求

任何进入 `performance.md`、Capacity Planning 或 SLO 的数字必须记录：

~~~text
commit:
machine / runner:
cpu model:
physical cores:
memory:
os:
jdk:
jvm flags:
tls mode:
payload:
concurrency:
connections per endpoint:
warmup:
measurement:
fork:
qps:
p50:
p99:
p99.9:
allocation/op:
gc:
cpu:
error rate:
~~~

没有以上环境元数据，只能称为“本次实验结果”，不能写成项目性能结论。

## 9. V2-D.2 Acceptance

### Tooling

- [x] JMH payload × connection shard × concurrency Matrix Runner；
- [x] sample + throughput；
- [x] GC/allocation profiler；
- [x] JSON 结果；
- [x] CSV/Markdown 聚合；
- [x] 环境元数据记录；
- [x] 10k Virtual Thread Soak Runner；
- [x] Soak JSON schema；
- [x] Performance Evidence workflow；
- [x] PR smoke gate。

### Evidence

- [ ] 在固定硬件执行 full matrix；
- [ ] 固定硬件执行至少 30 分钟 10k soak；
- [ ] 建立 p50/p99/p99.9 + allocation/op 基线；
- [ ] overload / slow Provider / fault benchmark；
- [ ] TLS vs plaintext overhead matrix；
- [ ] 基于证据决定 Buffer ownership；
- [ ] 基于证据决定 Future/PendingRequest 重构；
- [ ] 形成 Capacity Planning Guide。

因此当前状态必须保持：

> **V2-D.2 In Progress**

不能因为 Runner 已实现就把“完整性能证据”写成 Current/Complete。

## 10. 文档防漂移规则

机器可读状态统一维护在：

~~~text
docs/capability-status.properties
~~~

以下文档必须包含一致的 `capability-status` 标记：

- `README.md`
- `README.en-US.md`
- `docs/production-roadmap.md`
- `docs/readiness.md`
- `docs/performance-kernel-v2d.md`
- 本文

`scripts/check_project.py` 会在状态不一致或出现已知过期表述时直接失败。

Production Roadmap 继续作为面向人的权威能力总表；properties 文件用于 CI 防止多个文档发生状态漂移。
