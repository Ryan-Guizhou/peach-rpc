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
- Python 3；
- OpenSSL；
- tracked Git 工作树干净；
- 稳定的 `PEACH_RPC_RUNNER_ID`；
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

## 3. 推荐执行

~~~bash
export PEACH_RPC_EVIDENCE_CLASS=controlled
export PEACH_RPC_RUNNER_ID=peach-rpc-perf-01
export PEACH_RPC_RUNNER_LABELS='self-hosted,linux,x64,peach-rpc-perf'

bash scripts/run_v2d2_e1_series.sh target/v2d2-e1-controlled
~~~

默认执行 3 次独立 controlled run；每次包含 Full Matrix 和 >=1800 秒 / 10000 logical callers Soak；Run 之间默认 cooldown 60 秒。

GitHub self-hosted Runner 也可以手工触发 `.github/workflows/performance-evidence.yml`。Controlled 模式必须为每次执行设置不同的 `run_id`，例如 `run-1`、`run-2`、`run-3`。

## 4. Evidence Bundle

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

## 5. Runner Baseline

Series 第一轮生成 `runner-baseline.json`，锁定 commit、Runner ID、CPU、核心数、NUMA、Memory、CPU governor、Kernel、Java、JVM flags 与 containerized 状态。后续 Run 在 Full Matrix 前比较，发生漂移立即终止。

## 6. Artifact 安全

TLS Matrix 使用测试用自签名证书。E1 后测试私钥只存在于系统临时目录，Benchmark 退出时删除，不上传到 Evidence Artifact。

## 7. 验收清单

### 工程能力

- [x] Runner Preflight；
- [x] Git commit 一致性；
- [x] Environment Baseline；
- [x] 唯一 Run ID；
- [x] Evidence 防覆盖；
- [x] SHA-256 Manifest / verify；
- [x] >=3 Run series orchestration；
- [x] REPORT_ONLY repeatability handoff；
- [x] TLS temporary-key artifact hygiene；
- [x] Evidence tooling CI self-test。

### 真实执行

- [ ] 专用固定 Runner 已确定；
- [ ] Run #1 Full Matrix + >=30m/10k Soak PASS；
- [ ] Run #2 Full Matrix + >=30m/10k Soak PASS；
- [ ] Run #3 Full Matrix + >=30m/10k Soak PASS；
- [ ] 三份 Manifest verify PASS；
- [ ] Runner baseline 全程无漂移；
- [ ] REPORT_ONLY repeatability 无 comparability failure。

只有真实执行全部完成，E1 才能结束并进入 V2-D.2-E2。
