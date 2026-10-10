# Transport FrameAccumulator Allocation Profiling 与热路径优化

> **状态：核心完整帧直通优化已在当前 main 代码中实现；受控性能量化仍待完成。**
> 当前新 CI 的 JMH `-prof gc` 与采样延迟属于 GitHub shared runner **Smoke Evidence**。未经固定主机多轮重复与端到端验证，不声明“内存分配下降 X%”或“p99 无回归”。

## 1. 优化目标与代码证据

OTRYX RPC 1.0.x 的 Vert.x TCP 传输使用 `FrameAccumulator`，并通过 `Consumer<byte[]>` 向 Core 交付完整帧。这个 `byte[]` 的所有权边界不能直接删除，否则可能让后续异步解码读取到 Netty 复用缓冲区。

优化前完整帧路径：

~~~mermaid
flowchart LR
    TCP[Vert.x inbound Buffer] --> Append[pending.appendBuffer]
    Append --> Header[Header parse]
    Header --> Copy[getBytes full frame]
    Copy --> Core[Core independent byte array]
~~~

当前完整帧路径：

~~~mermaid
flowchart LR
    TCP[Vert.x inbound Buffer] --> Pending{Pending partial bytes?}
    Pending -->|No| Parse[Parse incoming directly]
    Parse --> Copy[One independent frame byte array]
    Copy --> Core[Core]
    Parse --> Tail{Incomplete tail?}
    Tail -->|Yes| Save[Copy only incomplete tail into pending]
    Pending -->|Yes| Append[Legacy append and compact]
    Append --> Core
~~~

**明确的代码热点：** 旧实现即使对齐收到完整帧，仍执行一次 `pending.appendBuffer(incoming)`，随后 `pending.getBytes(...)` 创建完整帧数组。候选实现绕过前者，仍保留独立 `byte[]` 输出。优化目标是减少内存带宽和中间缓冲区分配，**不是**声称已经实现零拷贝。

## 2. 兼容性与内存安全

- Wire v1 Header、Codec ID、Payload、Handshake、Metadata、TLS 与 Deadline 格式**完全不改**。
- Header 仍使用复用的 32B 数组解析，`expectedFrameLength` 仍逐帧校验魔数、版本、长度和最大 Frame 尺寸。
- 当没有历史待完成帧时直接消费整个 incoming；如果输入末尾只有部分帧，复制尾部到 pending，以免持有 Netty Buffer 以供下轮修改。
- 当存在 pending 的部分帧时，保留既有 append/compact 分片重组流程。
- 完整帧始终通过 `source.getBytes(start,end)` 交付**独立 byte[]**，不将 Buffer slice 直接传到异步 Consumer/Provider Core。
- 收到帧在 Core 已准入后，PR-D 的 Frame 字节预算仍照常执行；该预算不包含正在累计的 Transport Buffer。

不优化属于其他子系统的 Future/PendingRequest、Fory Object[]，也不改 PR-D Admission 逻辑。

## 3. 定量 Allocation Profiling

仓库已经有 `otryx-benchmarks/.../FrameAccumulatorBenchmark`，参数包括：

| 维度 | 值 |
|---|---|
| Payload | 64、16384、1048576 bytes |
| Input | 完整帧 / 分片帧 |
| Benchmark mode | `avgt`、`sample` |
| JMH GC Profiler | `-prof gc`，读取 `gc.alloc.rate.norm`（B/op） |
| Latency | `avgt` ns/op、`sample` p99 ns |
| JVM | Java 21、`-Xms256m -Xmx256m` |
| Fork / threads | 每个测试一个 fork、单线程、短暂 warmup + measure |

执行命令（仓库根目录，需要包含 Base 和 Head 提交的完整 Git History；BASE_SHA 必须替换成真实的 40 位优化前提交，不能使用已删除的分支名）：

~~~bash
# 选择同时包含 FrameAccumulatorBenchmark、优化前源码的真实 Git 提交
BASE_SHA="<baseline-full-commit-sha>"
bash scripts/run_frame_allocation_comparison.sh \
  "$BASE_SHA" \
  "$(git rev-parse HEAD)"
~~~

脚本先为基线建独立 Git Worktree，从**同一仓库的明确 Base SHA**安装 `otryx-codegen` 并编译 Benchmark；然后在当前候选 SHA 上重复同样操作。两边使用同样 JDK、JMH 参数和 JVM flags；所有结果存放到：

~~~text
target/frame-allocation-comparison/
  environment.json
  baseline/avgt.json
  baseline/sample.json
  candidate/avgt.json
  candidate/sample.json
  comparison.md
~~~

`scripts/compare_frame_allocation.py` 校验两侧的 JMH 参数、JDK/JMH 版本、`gc.alloc.rate.norm` B/op、采样延迟 p99 和来源 SHA，拒绝空缺字段或冒充受控测量的输入。配置对应 [Frame Allocation Comparison GitHub Actions](../.github/workflows/frame-allocation-comparison.yml)。

**必须注意：** `FrameAccumulatorBenchmark` 仅包含本地 TCP 帧重组函数的微基准，JMH B/op 和 p99 不是完整 RPC 的 allocation/op 或端到端 p99；Shared Runner 测量也不具备固定物理宿主的一致性。它用于解释当前代码级热路径变化和方向性风险，不能单独完成整个 Transport 优化的性能验收。

## 4. 质量验证

~~~bash
mvn -B -ntp -pl otryx-codegen -am -DskipTests install
mvn -B -ntp -pl otryx-transport-vertx -am test
python3 scripts/test_frame_allocation_evidence.py
~~~

新增针对性用例验证：完整帧不引用输入 Buffer；粘包多个完整帧 + 部分尾帧；旧尾帧与新输入拼接；超过最大 Frame 的 Header 尽早拒绝；准确上限；非法 Magic；以及已存在的随机化分片/粘包循环。CI 同时运行既有 TLS、断链重连、独立 JVM、Rolling Compatibility 等路径。

## 5. 后续受控晋级门

正式宣称某一优化有净收益之前，需要在固定硬件上完成至少 3 组独立的 AB/BA 重复，每组采集：

- JMH `gc.alloc.rate.norm` 与真实 JFR/AsyncProfiler 分配热点；
- 端到端 RPC p50、p99、p99.9、QPS/core、CPU/GC/Native Memory；
- 64B～1MiB payload、多并发、多连接分片、TLS/mTLS 与压缩边界；
- 10k logical concurrency 长稳 30 分钟，超载及断链恢复；
- 性能变差时保留回滚/关闭候选优化的理由和真实证据。

该微基准对比脚本不能代替 [V2-D.2 受控 Evidence](performance-evidence.md) 或 [Dubbo 对照](dubbo-comparison.md)；没有独占 Runner 和可信原始数据时标为 **pending**，绝不填充推测值。
