# PR-C：OTRYX RPC 与 Apache Dubbo 独立进程对比基准

> **阶段：开发中 / Draft PR。** 下述脚本构成可执行的、独立 JVM 的 **Smoke 基线**，并不表示已完成固定硬件性能矩阵。任何 p99、QPS、吞吐领先结论必须建立在后续受控 Evidence 上。

## 1. 对比边界

| 项目 | OTRYX RPC | Apache Dubbo |
|---|---|---|
| 代码基线 | OTRYX RPC 2.0.0-SNAPSHOT，PR-A/PR-B 后 | 3.3.6（实验依赖固定） |
| 协议 | Peach Wire v1 / Vert.x TCP | Dubbo TCP |
| 序列化 | Fory Native | Hessian2 |
| 测试服务 | `byte[] echo(byte[])` | `byte[] echo(byte[])` |
| 服务发现 | 固定 Endpoint | 直连 URL |
| 传输加密 | 当前 Smoke 为明文 | 当前 Smoke 为明文 |
| Provider/Consumer | 独立 JVM，可跨机器 | 独立 JVM，可跨机器 |
| 负载模型 | 共用 `ComparisonHarness` | 共用 `ComparisonHarness` |

**重要：** 此对照衡量的是两套完整 RPC 栈的实用表现，而不是单独传输层效率，也不等价于相同序列化算法间的 A/B。未来另设控制不同 Codec、TLS、JVM、线程模型的矩阵，避免不公平结论。

### 执行拓扑

~~~mermaid
flowchart LR
    Bench[共同负载模型及统一 JSON Schema]
    CP[Peach Consumer JVM]
    PP[Peach Provider JVM]
    CD[Dubbo Consumer JVM]
    PD[Dubbo Provider JVM]
    V[校验器和 Evidence Report]

    Bench --> CP
    Bench --> CD
    CP -->|Wire v1 / Fory| PP
    CD -->|Dubbo TCP / Hessian2| PD
    CP --> V
    CD --> V
~~~

两组测试顺序执行，避免同机不同 Provider 进程互相抢占资源。正式测试应采用独占、固定硬件，控制 CPU 频率、JDK 版本、Heap 与 GC 参数、CPU Affinity、连接数与响应大小。

## 2. Smoke 快速开始

环境：JDK 21、Maven 3、Python 3、Bash、可访问 Maven Central 的网络。从仓库根目录运行：

~~~bash
bash scripts/run_rpc_comparison_smoke.sh
~~~

脚本的实际行为：

1. 编译并安装比较所需的 OTRYX RPC reactor 模块；
2. 独立构建 `benchmarks/rpc-comparison/otryx` 和 `benchmarks/rpc-comparison/dubbo`，输出各自独立的 shaded JAR；
3. 启动 Peach Provider JVM，执行独立 Consumer JVM，结束后停止；
4. 再以同样方式运行 Dubbo；
5. 生成 `target/rpc-comparison-smoke/` 下的 `peach.json`、`dubbo.json`、`environment.json`、`report.md` 和 Provider 日志。

默认 16 并发、预热 2 秒、测量 3 秒、256 字节数据包，**用于正确性和工具连通性检查，不能用于性能排名**。

可覆盖：

~~~bash
RPC_COMPARISON_CONCURRENCY=32 RPC_COMPARISON_WARMUP_SECONDS=5 \
RPC_COMPARISON_DURATION_SECONDS=15 RPC_COMPARISON_PAYLOAD_BYTES=1024 \
bash scripts/run_rpc_comparison_smoke.sh
~~~

## 3. 跨主机独立运行

构建完成后，将对应 JAR 部署到各自目标主机，启动独立 Provider：

~~~bash
# Peach Provider 主机
java -jar benchmarks/rpc-comparison/otryx/target/otryx-comparison.jar provider 19501

# Dubbo Provider 主机（另一个终端或实例）
java -jar benchmarks/rpc-comparison/dubbo/target/dubbo-comparison.jar provider 19502
~~~

在 Consumer 负载机执行，使用相同并发、预热和测量窗口：

~~~bash
export RPC_COMPARISON_GIT_SHA="$(git rev-parse HEAD)"
export RPC_COMPARISON_RUN_ID="local-lab-1"
export RPC_COMPARISON_EVIDENCE_CLASS="smoke"

java -jar benchmarks/rpc-comparison/otryx/target/otryx-comparison.jar \
  client <peach-provider-host> 19501 32 5 15 1024 peach.json

java -jar benchmarks/rpc-comparison/dubbo/target/dubbo-comparison.jar \
  client <dubbo-provider-host> 19502 32 5 15 1024 dubbo.json
~~~

两者均允许远程 TCP 直连；该路径没有额外认证，**只在隔离的性能实验网络运行**。如需 TLS，必须新增双方实际 TLS 配置及明确不同协议的比较条件；当前尚未覆盖。

## 4. Evidence 验证

~~~bash
python3 scripts/test_rpc_comparison_validator.py

python3 scripts/validate_rpc_comparison.py \
  --peach peach.json \
  --dubbo dubbo.json \
  --environment environment.json \
  --report report.md
~~~

校验器验证 schema、工作负载一致性、同一 SHA/Run ID、错误率、错误分类总和、百分位单调性、基础指标以及缺失数值的显式空值。

当前原生采集项为：throughput、p50/p99/p99.9、错误率与错误类型、Consumer JVM 进程 CPU 估算、GC 时间及内存起止采样。**未测得的** allocation/op、Server CPU/GC、实际连接数明示为 `null`，绝不伪造为 0。

当前负载为 **closed-loop** 且仅保留最后最多 100 万条成功延迟样本；存在 coordinated omission 与抽样窗口限制。要声称 p99 优势，需要进一步实现 open-loop 驱动和外部 JFR/allocation profiling。

## 5. 受控性能矩阵（后续验收门）

| 维度 | 目标配置 |
|---|---|
| Payload | 64B、256B、1KiB、16KiB、1MiB |
| 并发 | 1、32、128、512、1k、5k、10k |
| 场景 | NOOP、CPU、阻塞、异步、慢服务、过载 |
| 重复次数 | 每场景至少 3 次独立采样 |
| 长稳 | 10k 逻辑并发、至少 30 分钟 |
| 硬件 | 固定 CPU/内存/内核/网络拓扑/运行器指纹 |
| 必备指标 | QPS、p50/p99/p99.9、错误率、Client+Server CPU、GC、allocation/op、内存峰值、连接与 inflight |
| 变更控制 | 独立 JVM/主机、固定 JDK、同业务语义、相同数据与合理等价配置 |

这些属于**待实施的受控验收项目**，不是当前 Smoke 已经完成的测试。

已存在的 [V2-D.2 Evidence 工具](performance-evidence.md) 用于 Peach 自身定标；PR-C 另增加跨框架对照，不替代既有受控基线门禁。需要独占 Runner 与稳定网络后才能发布具有可复现条件的数值报告。

## 6. 外部实现依据

- [Apache Dubbo 官方 Java API 配置文档](https://dubbo.apache.org/zh-cn/overview/mannual/java-sdk/reference-manual/config/api/api/)：ServiceConfig、ReferenceConfig 与点对点直连。
- [Apache Dubbo 3.3.6 Maven Central](https://central.sonatype.com/artifact/org.apache.dubbo/dubbo/3.3.6)：版本依赖固定。

## 7. 已知风险

- 纯 Echo 对象基本无业务 CPU 消耗，只能测 RPC 调用完整路径；不同 Serializer 影响结论。
- 两侧 Provider 当前独立配置线程与 admission，压力测试需要按场景明确记录参数，不能说“默认等价”。
- Smoke 未衡量 JFR allocation、Server CPU、真实跨主机延迟或多负载强度。
- Provider Ready 文本代表进程启动成功，不替代端到端请求验证；Consumer 在正式测量前发送 Echo 探测。
- CI 所用 GitHub shared runner 仅供 Smoke，不得成为正式容量/SLO 基线。
