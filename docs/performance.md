# 性能基准与优化规则

<!-- capability-status:v2-d=in-progress -->
<!-- capability-status:v2-d2=in-progress -->

V2-D.2 的完整矩阵、allocation profiling、10k logical-concurrency soak 和 evidence workflow 见 [V2-D.2 Performance Evidence & 10k Soak](performance-evidence-v2d2.md)。

Peach RPC 不接受没有可重复环境信息的“高性能”结论。

验证至少分为：JMH 微基准、Raw Transport、端到端 RPC、故障/过载。需要记录 QPS/Core、p50/p99/p99.9、CPU、Allocation、GC、inflight、连接数和错误率。

## V2-B 已完成的热路径改造

1. Registry 不进入请求路径。
2. SPI 不在请求路径解析。
3. Generated Consumer Stub 优先于动态代理。
4. Generated Provider Dispatcher 优先于 MethodHandle。
5. 0~4 参数 Generated Consumer 使用专用 CallSite。
6. RpcMethodCodec 在 refer/register 阶段预绑定。
7. RpcFrameView 不复制 Metadata/Payload。
8. Fory 支持从 payload slice 解码。
9. Unary REQUEST/RESPONSE 使用协议专用编码。
10. ServiceDirectory 读取不可变语义的 ServiceInstance[]。
11. 内置 P2C/EWMA 不再构造 List/LoadBalanceContext。
12. Request ID 与 pending table 按连接 Event Loop 本地化。
13. 每 Endpoint 可配置多个连接分片。
14. Connection shard 选择不依赖全局 AtomicInteger。
15. TCP handshake 有独立超时。

## 已知仍存在的成本

V2-B 仍不是零分配：

- Fory Codec ID 1 的参数对象图仍是 Object[]；
- Codec 输出仍是 byte[]；
- Transport/Core 仍交换完整 byte[] frame；
- FrameAccumulator 需要重组完整帧；
- CompletableFuture/PendingRequest 按请求创建；
- EndpointStats 使用原子变量；
- Semaphore admission 为共享并发门；
- Provider 默认每请求提交虚拟线程任务。

这些成本按基准收益排序，而不是按“理论上可优化”排序。

## JMH 基准

当前包含：

- `InvocationPathBenchmark`：Generated / JDK Proxy / Byte Buddy；
- `ProtocolDecodeBenchmark`：full decode / RpcFrameView；
- `ProtocolEncodeBenchmark`：generic request encode / unary fast encode；
- `LoadBalancePathBenchmark`：List contexts / array + metrics；
- `EndToEndLatencyBenchmark`：单并发 Raw Vert.x loopback echo 与完整 Peach RPC echo。

构建：

```bash
mvn -B -ntp -pl peach-rpc-benchmarks -am clean package -DskipTests
```

运行示例：

```bash
java -jar peach-rpc-benchmarks/target/benchmarks.jar
```

正式结果必须记录机器型号、CPU 核数、JDK、JVM 参数、warmup、measurement、fork 和 payload 大小。

`EndToEndLatencyBenchmark.rawVertxEcho` 是网络/Vert.x loopback 基线，`peachRpcEcho` 包含 Generated Stub、Codec、协议、Transport、Provider Dispatcher 与返回解码。两者 AverageTime 差值用于观察当前机器上的 **RPC Added Latency**，仓库不提交未固定测试环境的百分比结论。

## V2-D 第一批基准矩阵

当前 V2-D 分支已增加：

- Protocol encode payload 参数：64B / 256B / 1KiB / 16KiB / 1MiB；
- Protocol decode payload 参数：64B / 256B / 1KiB / 16KiB / 1MiB；
- `requestIdHeaderFastPath`：固定 Header Request ID 无对象读取；
- `EndToEndPayloadBenchmark`：完整 Generated Stub / Fory / Protocol / Vert.x / Provider Dispatcher byte[] echo；
- `FrameAccumulatorBenchmark`：完整帧与 TCP 分片帧重组；
- `connectionsPerEndpoint` 参数：1 / 2 / 4 / 8；
- 并发通过 JMH `-t` 控制。

推荐先构建：

```bash
mvn -B -ntp -pl peach-rpc-benchmarks -am clean package -DskipTests
```

单点样例：

```bash
java -jar peach-rpc-benchmarks/target/benchmarks.jar EndToEndPayloadBenchmark \
  -p payloadSize=256 \
  -p connectionsPerEndpoint=1 \
  -t 16 \
  -prof gc \
  -rf json \
  -rff target/v2d-256b-t16-c1.json
```

并发矩阵建议：

```text
1 / 16 / 64 / 256 / 1024
```

10k concurrency 不直接使用 10k JMH platform threads，后续使用虚拟线程/异步 soak harness 验证，避免把 benchmark driver 自身线程调度成本误判为 RPC 成本。

### V2-D Header fast path

当前第一批已将 Transport 正常路由中的两个确定性分配移除：

1. Client response routing 不再通过 `ByteBuffer.wrap(...).getLong()` 读取 Request ID；
2. Server 正常 REQUEST tracking 不再执行完整 `RpcProtocolCodec.decode()` 创建 `RpcFrame`、Metadata Map 与 Payload copy；
3. `rewriteTimeoutBudgetMillis()` 不再创建 `RpcFrameView`，直接校验固定 Header/length 后原地刷新；
4. `FrameAccumulator` 复用固定 32B Header 数组，不再为每个 Frame 创建 Header byte[]。

统一改为：

```java
RpcProtocolCodec.readRequestId(frame)
```

该 accessor 只读取固定 Header；完整业务处理仍由 Core 使用 `RpcFrameView` 做协议校验和 payload slice decode，因此 v1 wire、Codec 与业务语义不变。

### 结果记录模板

每次准备形成项目级性能结论时至少记录：

```text
commit:
cpu:
physical cores:
memory:
os:
jdk:
jvm flags:
tls mode:
benchmark:
payload:
threads/concurrency:
connections per endpoint:
fork:
warmup:
measurement:
qps:
p50:
p99:
p99.9:
allocation/op:
gc:
cpu:
error rate:
notes:
```

## V2-D.2 当前证据工具

当前已提供：

- `scripts/run_v2d2_benchmark_matrix.sh`：smoke / standard / full Matrix；
- `scripts/summarize_v2d2_results.py`：JMH JSON 聚合为 CSV/Markdown；
- `scripts/run_v2d2_soak.sh`：Virtual Thread soak；
- `PerformanceSoakRunner`：默认 10000 logical concurrency；
- `.github/workflows/performance-evidence.yml`：可保存长期证据 Artifact；
- 普通 CI 的 benchmark smoke + 10k short soak smoke。

注意：共享 CI Runner 上的数据只验证工具和行为，不写入 Production SLO。固定硬件 full matrix / 长时间 soak 尚未完成，因此 V2-D.2 保持 In Progress。

## V2-D 后续候选

仍需继续：

1. Generated Dispatcher / MethodHandle 完整基准；
2. Fory generic / slice decode 与参数对象图 allocation 基准；
3. FrameAccumulator complete/fragmented frame 基准；
4. overload / slow consumer/provider；
5. retry/circuit/outlier 故障注入；
6. 10k concurrency 异步/虚拟线程 soak；
7. Buffer ownership 是否值得进入默认路径；
8. CompletableFuture/PendingRequest 是否值得重构。

所有优化必须通过基准证明收益，不能仅因为“理论上更快”进入默认路径。
