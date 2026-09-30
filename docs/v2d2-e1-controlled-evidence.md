# Peach RPC V2-D.2-E1 Controlled Performance Evidence Execution

> 状态：**In Progress / Automation Ready / Fixed-Hardware Runs Pending**  
> 前置：V2-D.2 benchmark/evidence tooling 已进入 main。  
> Exit Gate：同一固定 Runner 上至少 3 次 Full Matrix + >=30 分钟 10k Soak，且每个 Evidence Bundle 校验 PASS。

## 1. E1 的边界

E1 只负责采集可信原始证据，不负责根据单次结果修改 RPC 内核，也不定义 Production SLO。

~~~mermaid
flowchart LR
    P[Runner Preflight] --> B[Lock Runner Baseline]
    B --> R1[Controlled Run 1]
    R1 --> R2[Controlled Run 2]
    R2 --> R3[Controlled Run 3]
    R3 --> V[Validate Every Bundle]
    V --> M[SHA-256 Manifests]
    M --> C[REPORT_ONLY Repeatability]
    C --> E2[V2-D.2-E2]
~~~

## 2. Runner 前置条件

必须满足：

- Linux；
- JDK 21；
- Maven 3.9+；
- 显式设置非空 `PEACH_RPC_JVM_FLAGS`，作为三次 Run 的固定 JVM 配置；
- Python 3；
- OpenSSL；
- tracked Git 工作树干净；
- 稳定的 `PEACH_RPC_RUNNER_ID`；
- 同一物理 Runner：Environment 会记录由 `/etc/machine-id` 与 DMI product UUID 组合后计算的 SHA-256 指纹，不上传原始机器标识；
- 三次 Run 使用同一个 commit；
- CPU / 核心数 / Memory / Kernel / JDK / JVM flags / governor 保持一致；
- 性能执行期间不与普通 CI 竞争同一组 CPU/Memory 资源。

可选严格约束：

~~~bash
export PEACH_RPC_EXPECT_CPU_MODEL='<exact model>'
export PEACH_RPC_EXPECT_PHYSICAL_CORES='<count>'
export PEACH_RPC_EXPECT_LOGICAL_CORES='<count>'
export PEACH_RPC_EXPECT_CPU_GOVERNOR='<governor>'
export PEACH_RPC_MIN_MEMORY_BYTES='<bytes>'
export PEACH_RPC_REQUIRE_BARE_METAL=true
~~~

这些值必须来源于实际 Runner，仓库不预设机器型号。

## 3. Runner Qualification

首次配置固定 Runner 时，先只执行轻量 Preflight，取得该机器的 host fingerprint：

~~~bash
export PEACH_RPC_EVIDENCE_CLASS=controlled
export PEACH_RPC_RUNNER_ID=peach-rpc-perf-01
export PEACH_RPC_JVM_FLAGS='<fixed JVM flags for this runner>'
export PEACH_RPC_BENCHMARK_COMMIT="$(git rev-parse HEAD)"

bash scripts/preflight_v2d2_runner.sh target/v2d2-runner-qualification
grep '^host_fingerprint_sha256=' \
  target/v2d2-runner-qualification/preflight.properties
~~~

记录该 SHA-256 值。GitHub controlled Performance Evidence Workflow 要求同时提供：

- 期望的 self-hosted `runner_id`；
- 该 Runner 的 `host_fingerprint_sha256`；
- 三轮完全相同的 `jvm_flags`。

Workflow 在 Full Matrix 开始前同时验证真实 `runner.name` 和物理主机指纹，从而避免第二、三轮被调度到另一台同配置机器后才发现证据不可比较。

## 4. 推荐执行

~~~bash
export PEACH_RPC_EVIDENCE_CLASS=controlled
export PEACH_RPC_RUNNER_ID=peach-rpc-perf-01
export PEACH_RPC_RUNNER_LABELS='self-hosted,linux,x64,peach-rpc-perf'
export PEACH_RPC_JVM_FLAGS='<fixed JVM flags for this runner>'

bash scripts/run_v2d2_e1_series.sh target/v2d2-e1-controlled
~~~

默认执行 3 次独立 controlled run；每次包含 Full Matrix 和 >=1800 秒 / 10000 logical callers Soak；Run 之间默认 cooldown 60 秒。三轮完成后不会直接进入 E2，而是先调用 `finalize_v2d2_e1.py` 生成机器可读的 E1 Handoff。

GitHub self-hosted Runner 也可以手工触发 `.github/workflows/performance-evidence.yml`。Controlled 模式中的 `runner_id` 是**期望的 GitHub self-hosted Runner 名称**；Workflow 会读取真实 `runner.name` 并要求二者完全一致，不能用手填 ID 冒充固定 Runner。每次执行还必须设置不同的 `run_id`，例如 `run-1`、`run-2`、`run-3`。

## 5. Evidence Bundle

每次 Run 至少包含：

~~~text
preflight.properties
preflight.md
environment.properties
environment.md
matrix/
soak.json
validation-report.json
validation-report.md
decision-inputs.json
decision-inputs.md
evidence-manifest.json
~~~

Manifest 校验：

~~~bash
python3 scripts/manage_v2d2_evidence_manifest.py verify --bundle <bundle>
~~~

## 6. Runner Baseline

Series 第一轮生成 `runner-baseline.json`，锁定 commit、Runner ID、**host fingerprint SHA-256**、CPU、核心数、NUMA、Memory、CPU governor、Kernel、Java、JVM flags 与 containerized 状态。后续 Run 在 Full Matrix 前比较，发生漂移立即终止。即使两台机器配置完全相同，只要主机指纹不同，也不会被当成同一固定 Runner。

`PEACH_RPC_JVM_FLAGS` 不是只写入报告：Matrix 与 Soak 脚本会把它设置为子 Java 进程的 `JAVA_TOOL_OPTIONS`，因此 JMH fork 与 Soak JVM 使用的就是 Evidence 中记录的同一组 flags。Controlled Evidence 禁止空 JVM 配置。

## 7. E1 Handoff Gate

`finalize_v2d2_e1.py` 是 E1 的显式退出门禁。它要求：

- 至少 3 份 controlled Evidence Bundle；
- 每份 `validation-report.json` 为 PASS；
- 每份 SHA-256 Manifest verify PASS；
- 每份具有唯一、非 unknown 的 `run_id`；
- Soak concurrency >=10000；
- Soak duration >=1800 秒；
- 每份 Soak 存在成功请求；
- commit / Runner ID / host fingerprint / hardware / JDK / JVM flags 等可比较；
- threshold-free repeatability 状态必须为 `REPORT_ONLY` 且没有 comparability failure。

输出：

~~~text
e1-handoff/
├── e1-handoff.json
├── e1-handoff.md
└── repeatability-report-only/
~~~

`e1-handoff.status=PASS` 只表示 **V2-D.2-E1 原始证据采集完成**。它不会定义 CV 阈值、不会晋级 Baseline，也不会建立 Production SLO；这些属于 V2-D.2-E2。

## 8. Artifact 安全

TLS Matrix 使用测试用自签名证书。E1 后测试私钥只存在于系统临时目录，Benchmark 退出时删除，不上传到 Evidence Artifact。

## 9. 验收清单

### 工程能力

- [x] Runner Preflight；
- [x] Git commit 一致性；
- [x] explicit JVM flags recorded-and-applied gate；
- [x] Environment Baseline；
- [x] GitHub actual `runner.name` 与 expected runner name 一致性校验；
- [x] hashed physical-host fingerprint；
- [x] expected host fingerprint fail-fast gate；
- [x] 唯一 Run ID；
- [x] Evidence 防覆盖；
- [x] SHA-256 Manifest / verify；
- [x] >=3 Run series orchestration；
- [x] REPORT_ONLY repeatability handoff；
- [x] explicit E1 Handoff Gate；
- [x] TLS temporary-key artifact hygiene；
- [x] Evidence tooling CI self-test。

### 真实执行

- [ ] 专用固定 Runner 已确定；
- [ ] Run #1 Full Matrix + >=30m/10k Soak PASS；
- [ ] Run #2 Full Matrix + >=30m/10k Soak PASS；
- [ ] Run #3 Full Matrix + >=30m/10k Soak PASS；
- [ ] 三份 Manifest verify PASS；
- [ ] Runner baseline 全程无漂移；
- [ ] REPORT_ONLY repeatability 无 comparability failure；
- [ ] `e1-handoff.status=PASS`。

只有真实执行全部完成，E1 才能结束并进入 V2-D.2-E2。
