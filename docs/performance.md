# 性能基准与优化规则

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

## 下一批基准

需要继续增加：

1. RPC no-op 64B / 256B / 1KiB / 16KiB / 1MiB；
2. concurrency 1 / 16 / 64 / 256 / 1024 / 10000；
3. Generated Dispatcher / MethodHandle；
4. Fory generic / slice decode；
5. connection count 1 / 2 / 4 / 8；
6. overload / slow consumer；
7. retry/circuit/outlier 故障注入；
8. GC 与 allocation profiler。

所有优化必须通过基准证明收益，不能仅因为“理论上更快”进入默认路径。
