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

完整证据链：

~~~text
Environment fingerprint
        |
        v
Full JMH matrix + GC profiler
        |
        v
30m+ / 10k soak
        |
        v
Evidence validator
        |
        v
Decision inputs
        |
        +--> Buffer ownership gate
        +--> Future/PendingRequest gate
        +--> Capacity planning numbers
~~~

`validation-report.*` 只证明证据包结构、来源和维度完整；`decision-inputs.*` 只汇总描述性数据，不会自动宣告 Production SLO。

## 3. JMH 完整性能矩阵

矩阵由两部分组成：

1. **Payload Matrix**：64B ~ 1MiB × connection shard × concurrency；
2. **Execution/Fault Matrix**：NOOP / CPU / BLOCKING / SLOW_PROVIDER / OVERLOAD × connection shard × concurrency；
3. **Security Matrix**：PLAINTEXT / TLS × payload × connection shard × concurrency；
4. **Resilience Primitive Matrix**：Retry Budget / Circuit Breaker / Outlier Ejection 的成功、拒绝、失败记账与可用性读路径。

OVERLOAD 场景通过 JMH AuxCounters 记录 success/error，不让预期过载异常直接终止整个 benchmark。

入口：

~~~bash
bash scripts/run_v2d2_benchmark_matrix.sh <profile> <output-directory>
~~~

可用 Profile：

| Profile | Payload | Connections | Threads | Mode | 用途 |
|---|---|---|---|---|---|
| `smoke` | 256B + NOOP | 1 | 1 | sample | PR/CI 工具链验证 |
| `standard` | 64B / 1KiB / 16KiB + 全部场景 | 1 / 4 | 1 / 64 / 256 | sample + thrpt | 日常性能回归 |
| `full` | 64B / 256B / 1KiB / 16KiB / 1MiB + 全部场景 | Payload 1/2/4/8；Scenario 1/4 | 1 / 16 / 64 / 256 / 1024 | sample + thrpt | V2-D.2 完整证据 |

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

建议固定专用 Runner 后，把 full matrix + 长时间 soak 作为发布前证据门禁。Performance Evidence workflow 的 soak 默认时长为 1800 秒；共享 Runner 仍只用于工具链验证，不用于形成 Production SLO。

### 7.1 固定 Runner 模式

Performance Evidence workflow 支持：

- `evidence_class=shared-ci|controlled`；
- `runner_labels_json`，例如 `["self-hosted","linux","x64","peach-rpc-perf"]`；
- `runner_id`，在 controlled GitHub Workflow 中表示**期望的 self-hosted Runner 名称**；实际证据使用 GitHub `runner.name`；

当 `evidence_class=controlled` 时会 fail-fast 要求：

- 不能使用 `ubuntu-latest`；
- 必须使用稳定 `runner_id`，并在 GitHub self-hosted Workflow 中校验 expected runner name == actual `runner.name`；
- 必须生成非 `unknown` 的 `host_fingerprint_sha256`；该值只保存哈希，不保存原始 machine-id / DMI UUID；
- GitHub controlled Workflow 必须预先提供 expected `host_fingerprint_sha256`，并在 Full Matrix 前与当前机器计算值一致；
- 必须 `matrix_profile=full`；
- Matrix 与 Soak 必须同时运行；
- concurrency >= 10000；
- soak >= 1800 秒。

固定 Linux 主机也可直接执行：

~~~bash
export PEACH_RPC_EVIDENCE_CLASS=controlled
export PEACH_RPC_RUNNER_ID=<stable-runner-id>
export PEACH_RPC_RUNNER_LABELS='local-fixed-linux'
bash scripts/run_v2d2_fixed_evidence.sh target/v2d2-fixed-evidence
~~~

该入口依次完成 Build -> 环境指纹 -> Full Matrix -> 30m Soak -> Evidence Validation -> Decision Inputs。

### 7.2 V2-D.2-E1 固定证据执行

E1 增加真实固定 Runner 所需的执行约束：

- `preflight_v2d2_runner.sh` 校验 Linux、JDK 21、Maven 3.9+、Git commit、expected/actual Runner ID，并生成 hashed physical-host fingerprint；同时支持约束 CPU/核心数/内存/governor；
- `check_v2d2_runner_baseline.py` 在首轮锁定 commit、Runner ID、host fingerprint、CPU、Memory、Kernel、JDK、JVM flags 等稳定字段，后续 Run 在重型测试前 fail-fast；
- `manage_v2d2_evidence_manifest.py` 为 Evidence Bundle 生成并校验 SHA-256 Manifest；
- `run_v2d2_e1_series.sh` 顺序执行至少 3 次完整 controlled run，并输出 REPORT_ONLY repeatability；
- Evidence 目录不允许静默覆盖；
- TLS benchmark 测试私钥使用系统临时目录并在退出时删除，不进入 Artifact。

固定 Runner 推荐入口：

~~~bash
export PEACH_RPC_EVIDENCE_CLASS=controlled
export PEACH_RPC_RUNNER_ID=peach-rpc-perf-01
export PEACH_RPC_RUNNER_LABELS='self-hosted,linux,x64,peach-rpc-perf'

bash scripts/run_v2d2_e1_series.sh target/v2d2-e1-controlled
~~~

可选严格硬件约束必须来自实际 Runner：

~~~bash
export PEACH_RPC_EXPECT_CPU_MODEL='<exact model>'
export PEACH_RPC_EXPECT_PHYSICAL_CORES='<count>'
export PEACH_RPC_EXPECT_LOGICAL_CORES='<count>'
export PEACH_RPC_EXPECT_CPU_GOVERNOR='<governor>'
export PEACH_RPC_MIN_MEMORY_BYTES='<bytes>'
~~~

E1 的 repeatability 保持 **REPORT_ONLY**；CV 阈值与 Baseline Promotion 属于 V2-D.2-E2。

三次真实证据收集完成后必须执行 `finalize_v2d2_e1.py`。只有 `e1-handoff.json` 状态为 PASS 时，E1 才能向 E2 移交；该 PASS 只代表证据采集完整，不代表性能稳定性阈值或 Production Baseline 已建立。

### 7.3 重复性验证

单次 controlled run 只能形成“可比较证据”，不能直接形成生产基线。正式基线至少执行 3 次独立 controlled run，然后执行：

~~~bash
python3 scripts/compare_v2d2_evidence.py \
  --run target/evidence-run-1 \
  --run target/evidence-run-2 \
  --run target/evidence-run-3 \
  --output-dir target/v2d2-repeatability
~~~

默认进入 **REPORT_ONLY** 模式：

- 校验 commit、runner、CPU、核心数、内存、kernel、JDK、JVM flags 等环境一致性；
- 校验三次 Matrix point 集合完全一致；
- 汇总 soak throughput / p50 / p99 / p99.9 / CPU / GC 的 CV 与 spread；
- 汇总全部 Matrix score 与 allocation/op 的 run-to-run CV；
- 输出 `repeatability-report.json/.md`。

项目当前**不预设拍脑袋的稳定性阈值**。获得真实固定硬件数据后，可显式传入门槛：

~~~bash
python3 scripts/compare_v2d2_evidence.py \
  --run target/evidence-run-1 \
  --run target/evidence-run-2 \
  --run target/evidence-run-3 \
  --max-matrix-score-cv-percent <threshold> \
  --max-matrix-allocation-cv-percent <threshold> \
  --max-soak-throughput-cv-percent <threshold> \
  --max-soak-p99-cv-percent <threshold> \
  --output-dir target/v2d2-repeatability
~~~

只有显式提供阈值且全部通过时，repeatability report 才为 **PASS**；未设置阈值时为 **REPORT_ONLY**，避免工具擅自定义 Production Gate。

当 repeatability report 为 PASS 后，可以生成候选基线：

~~~bash
python3 scripts/promote_v2d2_baseline.py \
  --repeatability-report target/v2d2-repeatability/repeatability-report.json \
  --output-dir target/v2d2-baseline-candidate
~~~

输出 `baseline-candidate.json/.md`。状态始终是 **CANDIDATE**，不能直接等价为 Production SLO；仍需人工评审阈值策略、TLS/Fault 证据、allocation/GC、容量规划和 V2-D.3 优化决策。

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

### Evidence tooling

- [x] 固定 Runner / controlled evidence workflow gate；
- [x] CPU/内存/JDK/Runner 环境指纹；
- [x] Evidence bundle 完整性与来源校验；
- [x] Decision Inputs JSON/Markdown 自动生成；
- [x] 单命令 fixed-runner evidence orchestrator；
- [x] controlled evidence 跨运行环境一致性与 repeatability 分析器；
- [x] repeatability PASS -> baseline candidate 的显式晋级门禁。

### Evidence

- [ ] 在固定硬件执行 full matrix；
- [ ] 固定硬件执行至少 30 分钟 10k soak；
- [ ] 至少 3 次 controlled run 并形成 repeatability report；
- [ ] 建立 p50/p99/p99.9 + allocation/op 基线；
- [x] overload / slow Provider / CPU / blocking benchmark tooling；
- [x] Retry Budget / Circuit Breaker / Outlier Ejection resilience primitive matrix tooling；
- [x] TLS vs plaintext overhead matrix tooling；
- [ ] 固定环境采集 retry / circuit / outlier 与 Provider fault 的完整数据；
- [ ] 基于证据决定 Buffer ownership；
- [ ] 基于证据决定 Future/PendingRequest 重构；
- [x] Capacity Planning Guide 方法论与数据输入模板；
- [ ] 用固定环境数据填充 Capacity Planning 的生产数值建议。

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

容量规划方法、公式、证据输入与发布门禁见 [Capacity Planning Guide](capacity-planning.md)。该文档当前提供方法论，不包含未经固定硬件验证的生产数值。
