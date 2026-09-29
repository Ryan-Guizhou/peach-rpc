# Peach RPC V2-D Performance Kernel Second Pass

> 状态：**Implementation Baseline / In Progress**  
> 基线：`main@181c7a6e890eb73f4ff78281d92c79cb2c749c03`  
> 目标：在不破坏 V2-C.3 高可用、安全、可观测性闭环以及 v1 wire 兼容性的前提下，完成第二轮性能内核优化。

## 1. Summary

V2-D 不以“理论零分配”为目标，而以**可重复 benchmark 证据驱动的尾延迟、吞吐与 allocation 优化**为目标。

本阶段遵循：

> **Benchmark first, API change second. Wire change last.**

第一批优先处理无需修改 Codec wire 的确定性成本：

1. 扩充 payload / connection shard / concurrency JMH 基线；
2. Transport 请求/响应路由避免完整 `RpcFrame` 解码和临时 `ByteBuffer`；
3. 建立 allocation profiler 运行方式；
4. 为 FrameAccumulator、CompletableFuture/PendingRequest、Buffer ownership 建立候选基线；
5. 只有 benchmark 证明收益后，才进入更复杂的 ownership/API 改造。

## 2. Current

当前热路径已经具备：

- Registry/SPI 不进入每请求路径；
- Generated Consumer Stub / Provider Dispatcher；
- 0~4 参数 Generated Invocation；
- 方法级 `RpcMethodCodec` 预绑定；
- `RpcFrameView` payload slice decode；
- Unary request/response 专用编码；
- Connection-local Request ID / pending table；
- connection sharding；
- P2C/EWMA array snapshot；
- Deadline、Retry、Circuit、Outlier、TLS、Tracing。

当前仍存在：

- Transport Server 为了 requestId 对完整 REQUEST 执行 `RpcProtocolCodec.decode()`，会创建 `RpcFrame`、Metadata Map 与 payload copy；
- Transport Client 为读取 response requestId 每帧创建 `ByteBuffer.wrap(...)`；
- FrameAccumulator 解析每个 frame 时复制固定 32B header，并为完整 frame 生成 `byte[]`；
- Fory Codec ID 1 仍以 `Object[]` 参数对象图作为 wire payload；
- Consumer/Transport 每调用存在多层 `CompletableFuture` 与 `PendingRequest`；
- Provider admission / EndpointStats 仍使用共享同步原语。

## 3. Goals

### D1 — Measurement foundation

建立可重复 JMH 基线：

- Payload：64B / 256B / 1KiB / 16KiB / 1MiB；
- Connection shard：1 / 2 / 4 / 8；
- Concurrency：1 / 16 / 64 / 256 / 1024；
- 10k concurrency 不直接使用 10k JMH platform threads，后续使用异步/虚拟客户端 soak harness；
- 使用 `-prof gc` 采集 allocation/GC；
- 输出 JSON 结果，禁止在未固定环境时提交百分比性能结论。

### D2 — Allocation-light protocol/transport routing

在不改变 v1 wire 的前提下：

- 增加固定 Header 字段的无对象 fast accessor；
- Client response routing 直接读取 requestId；
- Server request tracking 直接读取 requestId；
- 正常 REQUEST Transport 路径不再构造兼容层 `RpcFrame`；
- 兼容 `decode()` API 保留。

### D3 — Candidate optimization experiments

仅当 D1/D2 数据证明值得继续时评估：

- FrameAccumulator header-copy removal；
- Buffer-oriented / ownership-aware transport boundary；
- 减少 per-request CompletableFuture 层级；
- PendingRequest primitive/container 优化；
- EndpointStats/admission 争用优化；
- Fory 参数 Object[] 的替代表示。

## 4. Non-goals

本阶段第一批明确不做：

- 修改 v1 固定 32B Header；
- 改变 Fory Codec ID 1 wire payload；
- Stable Type ID / Schema fingerprint；
- 新增 Codec/Registry；
- 默认启用 compression；
- 为追求 benchmark 数字关闭 TLS/Deadline/Backpressure 等生产保护。

Stable Type ID、Schema fingerprint 与 rolling compatibility 属于 V2-E。

## 5. Architecture

~~~mermaid
flowchart LR
    App[Generated Consumer Stub] --> Codec[RpcMethodCodec]
    Codec --> Protocol[Unary Protocol Encode]
    Protocol --> Client[Vert.x Client]
    Client --> Wire[TCP / TLS]
    Wire --> Server[Vert.x Server]
    Server --> Core[PeachRpcServer]
    Core --> View[RpcFrameView]
    View --> Decode[Payload Slice Decode]
    Decode --> Dispatch[Generated Dispatcher]

    Bench[JMH Matrix] -. measures .-> Codec
    Bench -. measures .-> Protocol
    Bench -. measures .-> Client
    Bench -. measures .-> Server
~~~

V2-D 第一批只优化红线之外的本地对象/复制成本，不改变网络协议语义。

## 6. D1 Benchmark Matrix

### 6.1 Micro

- `ProtocolEncodeBenchmark`
  - payload 参数化；
  - generic vs unary fast encode。
- `ProtocolDecodeBenchmark`
  - payload 参数化；
  - full decode vs frame view vs header accessor。
- `InvocationPathBenchmark`
  - Generated / JDK / Byte Buddy。
- `LoadBalancePathBenchmark`
  - endpoint count 扩展。
- `FrameAccumulatorBenchmark`
  - complete frame / fragmented frame；
  - 多 payload。

### 6.2 End-to-end

新增 byte[] echo benchmark：

- payloadSize：64 / 256 / 1024 / 16384 / 1048576；
- connectionsPerEndpoint：1 / 2 / 4 / 8；
- JMH thread count 由 CLI 控制；
- AverageTime/SampleTime 与 Throughput 分开执行。

推荐命令：

~~~bash
mvn -B -ntp -pl peach-rpc-benchmarks -am clean package -DskipTests
java -jar peach-rpc-benchmarks/target/benchmarks.jar EndToEndPayloadBenchmark -p payloadSize=256 -p connectionsPerEndpoint=1 -t 16 -prof gc -rf json -rff target/v2d-256b-t16-c1.json
~~~

正式记录必须附：

- CPU / 核数；
- RAM；
- OS；
- JDK；
- JVM flags；
- benchmark commit；
- fork / warmup / measurement；
- TLS/plaintext；
- payload；
- concurrency；
- connectionsPerEndpoint。

## 7. D2 Header Fast Path

新增 Core 固定 Header 只读 accessor：

~~~text
REQUEST/RESPONSE byte[]
        |
        +-- requestId fast read
        |
        +-- normal Core path -> RpcFrameView
        |
        +-- compatibility path -> RpcFrame
~~~

设计约束：

- accessor 只读取固定 Header，不复制 body；
- 至少校验 header 长度；
- Transport 在 FrameAccumulator 已完成 frame 边界校验后使用；
- Core 业务处理仍使用 `RpcFrameView` 完整校验；
- `decode()` 保持兼容。

预期减少：

- Server 每正常 REQUEST 一次 `RpcFrame`；
- Server 每正常 REQUEST 的 Metadata/Payload copy；
- Client 每 RESPONSE 一次临时 `ByteBuffer`。

## 8. Compatibility

- v1 wire：不变；
- Codec ID：不变；
- Fory payload：不变；
- Spring 配置：不变；
- Registry：不变；
- TLS/mTLS：不变；
- Trace metadata：不变。

因此该批次允许 N/N 节点无感升级，不需要 Registry version/group 切换。

## 9. Testing

### Functional

- header accessor 正常 frame；
- short header fail-fast；
- requestId rewrite/read round-trip；
- multiplexed RPC regression；
- CANCEL/GO_AWAY/Heartbeat regression；
- TLS/mTLS regression；
- independent JVM / Nacos recovery regression。

### Performance evidence

- Protocol header accessor JMH；
- payload matrix JMH；
- GC profiler；
- 不提交未经固定环境复现的“提升 xx%”结论。

## 10. Acceptance Gates

### D1

- [ ] payload 参数化 Protocol benchmarks；
- [ ] end-to-end byte[] payload benchmark；
- [ ] connection shard 参数化；
- [ ] allocation profiler command documented；
- [ ] benchmark result metadata template。

### D2

- [ ] header requestId fast accessor；
- [ ] Client routing 移除 `ByteBuffer.wrap`；
- [ ] Server normal request routing 移除 full `RpcFrame.decode`；
- [ ] compatibility tests；
- [ ] Transport tests；
- [ ] `mvn -B -ntp clean verify -Pquality`。

### D3

- [ ] FrameAccumulator benchmark；
- [ ] Buffer ownership 是否进入默认路径有数据结论；
- [ ] CompletableFuture/PendingRequest 是否重构有 allocation 证据；
- [ ] Object[] 是否值得以 wire/API 复杂度交换有数据结论。

## 11. Rollback

本批优化只替换本地读取路径。

出现回归时可以直接恢复 Transport 使用：

~~~text
RpcProtocolCodec.decode(frame)
ByteBuffer.wrap(frame, requestIdOffset, 8)
~~~

不需要数据迁移、配置迁移或 wire rollback。

## 12. Implementation Sequence

1. 建立本计划书；
2. 扩充 Protocol/End-to-end benchmark；
3. 增加 Header fast accessor；
4. 切换 Client response routing；
5. 切换 Server request tracking；
6. 增加回归测试；
7. 更新 `performance.md` 与 Production Roadmap；
8. 跑 Repository checks；
9. 跑完整 Maven Reactor + `-Pquality`；
10. 跑 independent JVM/Nacos recovery；
11. 根据 benchmark 数据决定是否进入 FrameAccumulator/Buffer ownership 第二批。
