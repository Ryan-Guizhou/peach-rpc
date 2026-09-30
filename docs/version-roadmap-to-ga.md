# Peach RPC V2-D.2 → 1.0 GA 版本执行路线

> 状态：**Approved Plan / Not Yet Implemented**  
> 本文定义 Peach RPC 从当前 V2-D.2 到 1.0.0 GA 的**版本执行顺序、范围、依赖与验收标准**。  
> 当前能力状态仍以 [Production Roadmap / Capability Matrix](production-roadmap.md) 为唯一事实总表；本文只定义后续版本“要做什么”，不把计划项视为已实现能力。

## 1. 路线原则

后续版本统一遵循以下约束：

1. **Performance evidence before optimization**：V2-D.3 不预设必须实施某项优化，只有 V2-D.2-E2 的证据证明收益足够，候选项才能进入默认路径。
2. **Compatibility before ecosystem**：Stable Type ID、Schema Fingerprint、N/N+1、Rollback 未闭环前，不优先堆叠新的 Registry/Codec 生态。
3. **Robustness before GA**：malformed frame、fuzz、race、network blackhole/partition、long-running recovery 属于 1.0 GA 必选门禁。
4. **Operations before GA**：Capacity、SLO、Dashboard、Alert、Upgrade、Rollback、Recommended Defaults 必须在 RC 前可执行。
5. **Wire/API freeze at RC1**：进入 1.0.0-RC1 后，除明确修复兼容缺陷外，不再随意调整 Wire Protocol 与公开 Core API。
6. **Streaming remains Optional**：Unary RPC 是 1.0 GA 主线；Streaming 不阻塞 1.0，后续若进入范围必须独立设计流控、half-close、cancel 与 buffer limit。
7. **Strategic ecosystem after GA**：Protobuf/IDL、Kubernetes EndpointSlice、更多 Registry/Codec 默认放在 1.0 GA 之后。

## 2. 总版本链

~~~mermaid
flowchart LR
    E1[V2-D.2-E1\nControlled Evidence Execution]
    E2[V2-D.2-E2\nEvidence Analysis & Baseline]
    D3[V2-D.3\nEvidence-driven Kernel Optimization]
    D4[V2-D.4\nPerformance Closure]
    E11[V2-E.1\nWire Identity & Schema]
    E12[V2-E.2\nRolling Compatibility]
    F1[V2-F.1\nProtocol Robustness]
    F2[V2-F.2\nChaos & Recovery]
    G1[V2-G.1\nObservability & SLO]
    G2[V2-G.2\nProduction Operations]
    RC[1.0.0-RC1\nProduction Candidate]
    GA[1.0.0\nProduction GA]

    E1 --> E2 --> D3 --> D4 --> E11 --> E12 --> F1 --> F2 --> G1 --> G2 --> RC --> GA
~~~

> V2-D.3 是**条件执行版本**：如果 E2 证明当前内核没有足够收益空间，可缩减或跳过部分候选优化，但仍必须执行 V2-D.4 的性能收口与容量基线。

## 3. 版本总表

| 版本 | 状态 | 主题 | 核心产出 | 是否阻塞 1.0 GA |
|---|---|---|---|---|
| V2-D.2-E1 | **Next / Execution Pending** | Controlled Performance Evidence | >=3 份固定硬件完整 Evidence Bundle | 是 |
| V2-D.2-E2 | **Proposed** | Evidence Analysis & Baseline | Repeatability PASS + Baseline Candidate + 优化决策 | 是 |
| V2-D.3 | **Conditional Proposed** | Performance Kernel Optimization | 仅实现证据证明值得做的优化 | 条件性 |
| V2-D.4 | **Proposed** | Performance Closure | 最终性能基线、容量参数、回归证据 | 是 |
| V2-E.1 | **Proposed** | Wire Identity & Schema | Stable Type ID + Schema Fingerprint | 是 |
| V2-E.2 | **Proposed** | Rolling Compatibility | N/N+1 + Rollback + Compatibility Matrix | 是 |
| V2-F.1 | **Proposed** | Protocol Robustness | malformed/fuzz/property/race 测试闭环 | 是 |
| V2-F.2 | **Proposed** | Chaos & Recovery | 黑洞/分区/长稳恢复 + Registry Contract TestKit | 是 |
| V2-G.1 | **Proposed** | Observability & Production SLO | Error Model + Dashboard + Alert + SLO | 是 |
| V2-G.2 | **Proposed** | Production Operations & Release | Upgrade/Rollback/Recommended Defaults/Release Process | 是 |
| 1.0.0-RC1 | **Future** | Production Candidate | Wire/API Freeze + 全量生产门禁 | 是 |
| 1.0.0 | **Future** | Production GA | 正式生产发布 | - |
| 1.1.x+ | **Future** | Strategic Ecosystem | Protobuf/K8s/Compression/更多 Adapter | 否 |

---

## 4. V2-D.2-E1：Controlled Performance Evidence Execution

### 4.1 目标

在固定性能机器上产生第一批**可用于工程决策**的 Peach RPC 性能证据。该版本不以修改内核追求更好数字为目标。

### 4.2 Runner 基线

- [ ] 确定专用 Performance Runner；
- [ ] 固定 CPU / Memory / OS / Kernel；
- [ ] 固定 JDK 21 版本；
- [ ] 固定 JVM 参数；
- [ ] 固定 CPU governor；
- [ ] 固定 Runner ID；
- [ ] 固定 Benchmark commit；
- [ ] 明确后台服务与资源隔离规则；
- [ ] 避免与普通 CI 并发共享 CPU/Memory；
- [ ] 记录环境 fingerprint。

### 4.3 Full Matrix

每一次 controlled run 都必须覆盖：

**Payload**

- 64B；
- 256B；
- 1KiB；
- 16KiB；
- 1MiB。

**Connection shard**

- 1；
- 2；
- 4；
- 8。

**Concurrency**

- 1；
- 16；
- 64；
- 256；
- 1024；
- 10k logical callers 由 Soak 覆盖。

**Provider 场景**

- NOOP；
- CPU；
- BLOCKING；
- SLOW_PROVIDER；
- OVERLOAD。

**Security**

- PLAINTEXT；
- TLS。

**Resilience**

- Retry Budget；
- Circuit Breaker；
- Outlier Ejection。

### 4.4 执行任务

- [ ] Controlled Run #1 Full Matrix；
- [ ] Controlled Run #1 >=30min / 10k Soak；
- [ ] Controlled Run #2 Full Matrix；
- [ ] Controlled Run #2 >=30min / 10k Soak；
- [ ] Controlled Run #3 Full Matrix；
- [ ] Controlled Run #3 >=30min / 10k Soak；
- [ ] 每次生成 environment fingerprint；
- [ ] 每次生成 JMH JSON；
- [ ] 每次生成 summary.csv / summary.md；
- [ ] 每次生成 validation-report；
- [ ] 每次生成 decision-inputs；
- [ ] 三次 Evidence Validator 全部 PASS。

### 4.5 验收

最终至少保留：

~~~text
evidence-run-1/
evidence-run-2/
evidence-run-3/
~~~

每个目录必须是完整 controlled evidence bundle。

**禁止在本版本中因为单次数字不好而直接重构 Buffer/Future/PendingRequest。**

---

## 5. V2-D.2-E2：Evidence Analysis & Baseline Promotion

### 5.1 目标

把 E1 的原始数据转化为可重复的性能事实，并明确 V2-D.3 是否需要做、需要做什么。

### 5.2 重复性

- [ ] 校验三次 commit 一致；
- [ ] 校验 Runner / CPU / Memory / Kernel 一致；
- [ ] 校验 JDK / JVM flags 一致；
- [ ] 校验 Matrix point shape 一致；
- [ ] 统计每个 Matrix score CV / spread；
- [ ] 统计 allocation/op CV / spread；
- [ ] 统计 Soak throughput CV；
- [ ] 统计 p99 / p99.9 CV；
- [ ] 统计 QPS/Core 波动；
- [ ] 基于真实数据定义 Repeatability Threshold；
- [ ] Repeatability Report 达到 PASS。

### 5.3 性能分析

- [ ] QPS/Core；
- [ ] CPU saturation point；
- [ ] Connection shard 拐点；
- [ ] Payload 增长曲线；
- [ ] allocation/op 随 Payload 的变化；
- [ ] GC count/time 与 allocation rate；
- [ ] p50/p99/p99.9 退化曲线；
- [ ] TLS vs PLAINTEXT overhead；
- [ ] Slow Provider degradation；
- [ ] Overload degradation；
- [ ] Retry / Circuit / Outlier cost。

### 5.4 必须形成的工程决策

| 候选优化 | E2 必须给出结论 |
|---|---|
| Buffer Ownership / Buffer-oriented Codec | 做 / 不做 / 延后 |
| FrameAccumulator copy | 是否为主要瓶颈 |
| CompletableFuture 层级 | 是否值得降层 |
| PendingRequest | 是否值得重构 |
| Fory Object[] | 是否构成显著 allocation |
| EndpointStats / admission atomics | 是否出现 contention |
| Compression | 是否进入当前主线 |

### 5.5 验收

- [ ] Repeatability PASS；
- [ ] 生成真实 baseline candidate；
- [ ] 完成人工 Engineering Review；
- [ ] 明确 V2-D.3 Scope；
- [ ] 没有证据支持的优化不得进入 V2-D.3。

---

## 6. V2-D.3：Evidence-driven Performance Kernel Optimization

### 6.1 状态

**Conditional Proposed**

只实现 V2-D.2-E2 明确批准的候选项。

### 6.2 Candidate A：Buffer Ownership

仅当大 Payload 的 allocation/copy 被证明是主要瓶颈时实施：

- [ ] 定义 ownership 生命周期；
- [ ] retain/release 或等价机制；
- [ ] Transport -> Protocol -> Codec ownership 规则；
- [ ] timeout/cancel/reconnect/exception 释放；
- [ ] TLS 路径一致性；
- [ ] leak / double release / use-after-release 测试。

### 6.3 Candidate B：PendingRequest / Future

仅当小 Payload per-request allocation 被证明主要来自该路径时实施：

- [ ] PendingRequest 对象图分析；
- [ ] CompletableFuture allocation；
- [ ] timer / callback allocation；
- [ ] timeout / cancel 状态合并可行性；
- [ ] lightweight promise 可行性；
- [ ] 明确是否需要 pooling；
- [ ] 不允许为了“零分配”牺牲取消与异常语义。

### 6.4 Candidate C：Generated Path

- [ ] 消除证据证明有收益的剩余 Object[]；
- [ ] 扩展 specialized parameter path；
- [ ] primitive parameter / return 优化；
- [ ] >4 参数 generated path；
- [ ] Generated 与 fallback semantic parity。

### 6.5 Candidate D：FrameAccumulator

- [ ] complete-frame copy；
- [ ] fragmented-frame copy；
- [ ] partial buffer 生命周期；
- [ ] maxFrameBytes 安全边界；
- [ ] malformed frame 行为保持一致。

### 6.6 Candidate E：Concurrency Hotspot

只有 profiling 显示 contention 时处理：

- [ ] EndpointStats；
- [ ] inflight counter；
- [ ] admission counter；
- [ ] circuit state；
- [ ] retry budget；
- [ ] false sharing / cache-line contention。

### 6.7 验收规则

每个优化必须：

~~~text
Before Baseline
      |
      v
Optimization
      |
      v
Same Fixed Runner
      |
      v
Same Matrix
      |
      v
Repeatability
      |
      v
Regression Review
~~~

单次 JMH 更快不能作为合并依据。

---

## 7. V2-D.4：Performance Closure

### 7.1 目标

关闭整个 V2-D 性能阶段，并形成正式 Capacity Planning 输入。

### 7.2 任务

- [ ] 优化后 >=3 次 controlled evidence；
- [ ] Repeatability PASS；
- [ ] 与 V2-D.2 baseline 对比；
- [ ] 功能语义无回归；
- [ ] TLS 无回归；
- [ ] Retry/Circuit/Outlier 无回归；
- [ ] 最终 QPS/Core；
- [ ] 最终 p50/p99/p99.9；
- [ ] 最终 allocation/op；
- [ ] 最终 CPU/GC/Heap；
- [ ] Connection Shard 推荐区间；
- [ ] Provider maxConcurrent 推荐；
- [ ] CPU Pool 推荐；
- [ ] Queue Capacity 推荐；
- [ ] Timeout 推荐；
- [ ] Retry 推荐；
- [ ] Capacity Planning 填入真实数值；
- [ ] 形成 Production Recommended Profiles。

### 7.3 Exit Gate

完成后才允许：

> **V2-D = Current**

---

## 8. V2-E.1：Wire Identity & Schema

### 8.1 目标

建立不依赖 Classpath/SPI 顺序的稳定 Wire Identity。

### 8.2 任务

- [ ] Stable Type ID 规范；
- [ ] Framework/User ID namespace；
- [ ] reserved range；
- [ ] 显式注册边界；
- [ ] Type ID collision detection；
- [ ] duplicate registration fail-fast；
- [ ] Stable Type Registry；
- [ ] Fory Adapter 接入；
- [ ] Schema fingerprint；
- [ ] Method/request/response schema identity；
- [ ] fingerprint algorithm/version；
- [ ] HELLO/ACK 兼容能力扩展；
- [ ] schema mismatch fail-fast；
- [ ] 明确 wire error code；
- [ ] compatibility unit/integration tests。

### 8.3 设计约束

必须明确区分：

~~~text
Protocol Version
Codec ID
Type ID
Schema Fingerprint
Feature
~~~

不得把这些概念合并成一个“版本号”。

---

## 9. V2-E.2：Rolling Upgrade & Rollback Compatibility

### 9.1 Compatibility Matrix

至少验证：

~~~text
N Client   -> N Server
N Client   -> N+1 Server
N+1 Client -> N Server
N+1 Client -> N+1 Server
N+1        -> N rollback
~~~

### 9.2 任务

- [ ] N/N+1 compatibility contract；
- [ ] Protocol compatibility rules；
- [ ] Codec compatibility rules；
- [ ] Schema evolution rules；
- [ ] field add/remove 规则；
- [ ] request/response 双向兼容；
- [ ] capability negotiation；
- [ ] incompatible peer rejection；
- [ ] rolling Provider upgrade E2E；
- [ ] rolling Consumer upgrade E2E；
- [ ] mixed-version multi-Provider E2E；
- [ ] rollback E2E；
- [ ] Registry mixed-version metadata；
- [ ] Upgrade Guide；
- [ ] Rollback Guide；
- [ ] Compatibility Matrix。

---

## 10. V2-F.1：Protocol Robustness

### 10.1 Malformed Frame Matrix

- [ ] bad magic；
- [ ] unsupported version；
- [ ] unknown message type；
- [ ] unknown codec/compression；
- [ ] negative/overflow length；
- [ ] truncated header/payload；
- [ ] oversized frame；
- [ ] duplicate frame；
- [ ] invalid request ID；
- [ ] invalid metadata；
- [ ] invalid CANCEL/GO_AWAY/PING/PONG/HELLO；
- [ ] handshake ordering violation。

### 10.2 Fuzz / Property

- [ ] Protocol decoder fuzz；
- [ ] FrameAccumulator fuzz；
- [ ] HELLO fuzz；
- [ ] metadata fuzz；
- [ ] random fragmentation/coalescing；
- [ ] random CANCEL race。

### 10.3 Race Matrix

- [ ] timeout vs response；
- [ ] cancel vs response；
- [ ] cancel vs disconnect；
- [ ] reconnect vs directory update；
- [ ] drain vs new request；
- [ ] drain vs retry；
- [ ] close vs heartbeat；
- [ ] circuit half-open concurrent probe。

---

## 11. V2-F.2：Chaos & Recovery

### 11.1 Registry Chaos

**Etcd**

- [ ] network blackhole；
- [ ] partition；
- [ ] long disconnect；
- [ ] delayed response；
- [ ] Watch interruption；
- [ ] Lease renewal failure。

**Nacos**

- [ ] network blackhole；
- [ ] partition；
- [ ] auth-enabled；
- [ ] credential failure；
- [ ] delayed control plane；
- [ ] restart loop；
- [ ] subscription recovery。

### 11.2 Transport Chaos

- [ ] packet blackhole；
- [ ] half-open socket；
- [ ] Provider kill -9；
- [ ] Consumer network loss；
- [ ] reconnect storm；
- [ ] TLS handshake stall；
- [ ] certificate rotation under traffic。

### 11.3 Cluster Recovery

- [ ] multi-Provider rolling restart；
- [ ] partial Provider failure；
- [ ] large-ratio Provider failure；
- [ ] recovery storm；
- [ ] retry storm；
- [ ] circuit recovery；
- [ ] outlier recovery。

### 11.4 Registry Contract TestKit

- [ ] 定义统一 Registry 行为契约；
- [ ] Memory Adapter；
- [ ] Etcd Adapter；
- [ ] Nacos Adapter；
- [ ] capability-specific assertions；
- [ ] registration/discovery/subscription/recovery 共用测试矩阵。

---

## 12. V2-G.1：Observability & Production SLO

### 12.1 Error Model

- [ ] Error taxonomy；
- [ ] stable error code；
- [ ] Client / Provider / Transport / Registry / Security / Protocol 分类；
- [ ] Retry exhausted；
- [ ] Circuit open；
- [ ] Overloaded；
- [ ] 错误信息脱敏。

### 12.2 Metrics / SLO

至少形成：

- [ ] QPS；
- [ ] p50/p95/p99/p99.9；
- [ ] error rate；
- [ ] timeout rate；
- [ ] retry rate；
- [ ] circuit state；
- [ ] outlier count；
- [ ] active connections；
- [ ] reconnect；
- [ ] inflight；
- [ ] overload；
- [ ] Registry health；
- [ ] TLS handshake failure。

### 12.3 运维资产

- [ ] Grafana Dashboard；
- [ ] Alert Rules；
- [ ] Production SLO Template；
- [ ] Golden Signals；
- [ ] JFR Troubleshooting；
- [ ] OpenTelemetry Troubleshooting。

---

## 13. V2-G.2：Production Operations & Release

### 13.1 运维文档

- [ ] Capacity Planning 最终版；
- [ ] Upgrade Guide；
- [ ] Rollback Guide；
- [ ] Compatibility Matrix；
- [ ] Production Configuration Guide；
- [ ] Security Hardening Guide。

### 13.2 默认值与配置治理

- [ ] Recommended Defaults；
- [ ] dangerous option 标记；
- [ ] Starter configuration audit；
- [ ] secret/redaction review；
- [ ] credential error sanitization；
- [ ] timeout/retry/admission 默认值审查。

### 13.3 Release Engineering

- [ ] Examples 完整性；
- [ ] multi-instance rolling example；
- [ ] Maven deploy/release process；
- [ ] artifact verification；
- [ ] version policy；
- [ ] deprecation policy；
- [ ] changelog/release-note policy。

---

## 14. 1.0.0-RC1：Production Candidate

### 14.1 Freeze

进入 RC1 后：

- Wire Protocol Freeze；
- Public Core API Freeze；
- Codec/Type ID reserved range Freeze；
- 非兼容性修复不得随意改变上述边界。

### 14.2 RC Gate

**HA**

- [ ] Registry blackhole/partition；
- [ ] Transport recovery；
- [ ] rolling restart。

**Performance**

- [ ] fixed baseline；
- [ ] repeatability PASS；
- [ ] Capacity Planning。

**Compatibility**

- [ ] Stable Type ID；
- [ ] Schema Fingerprint；
- [ ] N/N+1；
- [ ] rollback。

**Robustness**

- [ ] fuzz；
- [ ] malformed；
- [ ] race；
- [ ] chaos；
- [ ] long soak。

**Operations**

- [ ] Dashboard；
- [ ] Alert；
- [ ] SLO；
- [ ] Upgrade；
- [ ] Rollback。

### 14.3 RC 验证

- [ ] RC soak；
- [ ] RC compatibility suite；
- [ ] RC chaos suite；
- [ ] RC examples；
- [ ] dependency/security audit；
- [ ] release artifact verification。

---

## 15. 1.0.0：Production GA

1.0.0 不再承载大型新功能，只处理 RC blocker 和最终发布。

### GA Gate

- [ ] RC Blocker = 0；
- [ ] Production GA Gate 全部满足；
- [ ] Wire Compatibility baseline 固定；
- [ ] Public API compatibility baseline 固定；
- [ ] Maven artifacts；
- [ ] source/javadoc artifacts；
- [ ] README 中英文同步；
- [ ] Production Roadmap 同步；
- [ ] Release Notes；
- [ ] Migration Guide；
- [ ] GA Tag。

完成后项目状态才允许从：

~~~text
Preview -> Release Candidate -> Production Ready
~~~

---

## 16. 1.1.x+：Strategic Ecosystem

以下能力默认不阻塞 1.0：

### 16.1 Protobuf / IDL

- Protobuf Codec；
- IDL；
- cross-language schema；
- generated stub；
- schema evolution。

### 16.2 Kubernetes

- EndpointSlice discovery；
- discovery-only Registry Capability；
- readiness / zone / node metadata；
- Kubernetes rolling deployment。

### 16.3 Compression

只有性能证据支持后再引入：

- LZ4；
- ZSTD；
- payload threshold；
- adaptive compression。

### 16.4 其他 Registry

- ZooKeeper；
- Consul；
- Eureka。

### 16.5 其他 Codec

- Kryo；
- Hessian2；
- Jackson/JSON。

---

## 17. 版本状态更新规则

每个版本开发前：

1. 在本文确认 Scope；
2. 在 Production Roadmap 标记当前阶段；
3. 不提前把 Planned 能力改成 Current。

每个版本完成后：

1. 代码、测试、Examples、文档必须同 PR；
2. 更新 Production Roadmap；
3. 更新 Readiness；
4. 若影响用户入口，同步中英文 README；
5. 更新 machine-readable capability status；
6. 执行 `python3 scripts/check_project.py`；
7. 执行 `mvn -B -ntp clean verify -Pquality`；
8. 涉及 Performance 时必须提供对应 Evidence；
9. 涉及 Wire/API Compatibility 时必须提供 compatibility test；
10. 不满足 Exit Gate 时不得进入下一版本。

## 18. 当前下一步

当前正式下一版本固定为：

> **V2-D.2-E1 — Controlled Performance Evidence Execution**

其目标不是继续增加 benchmark 工具，而是使用现有 controlled evidence 工具链，在固定硬件上取得至少三份完整、可比较的真实证据。
