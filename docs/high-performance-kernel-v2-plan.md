# Peach RPC 高性能内核 V2 实施计划

## 1. 背景

当前版本已经具备 SPI、长连接多路复用、本地服务目录、P2C/EWMA、Etcd 控制面和 Spring Boot Starter，但热路径仍然保留通用动态调用与通用 Codec 形态。V2 的目标不是立即堆满生态适配器，而是先稳定未来支持 Fory、Protobuf、Nacos、Etcd、ZooKeeper、Kubernetes、Generated Stub、Byte Buddy 等能力所依赖的 Core 契约。

## 2. 核心原则

1. 能在编译期确定的信息，不留到单次请求阶段。
2. 能在启动阶段绑定的 SPI、Codec 与服务目录，不进入热路径动态发现。
3. 注册中心只属于控制面，数据面只读取本地不可变快照。
4. 线协议 ID 一旦发布即视为 ABI，禁止按 Classpath 或 SPI 顺序动态分配。
5. 主流生态通过 Adapter 扩展，不把第三方类型泄漏到 Core。
6. 兼容能力不能强迫所有 Registry、Codec 和 Proxy 共享最低能力模型。

## 3. V2 分阶段计划

### V2-A：Core 契约稳定化（已完成并合并）

- Registry 拆分为 ServiceDiscovery 与 ServiceRegistrar。
- RegistryFactory 使用强类型 RegistryOptions，并预留 providerOptions 扩展袋。
- Registry 通过 Optional Registrar 支持 Kubernetes 等 discovery-only 实现。
- 增加 RegistryCapability / RegistryCapabilities。
- 固化 Codec 与 Compression 线协议 ID。
- 增加 RpcMethodDescriptor 与 RpcMethodCodec，允许 Codec 在服务方法粒度预绑定。
- 增加 HELLO / HELLO_ACK 协商模型与能力交集算法。
- 增加 Generated Client 运行时契约与注解处理器入口。
- 保留 JDK Proxy 作为未生成 Stub 时的兼容 fallback。
- 保持现有 Vert.x / Etcd / Fory / Starter 可编译可运行。

### V2-B：热路径替换（当前 PR）

已完成：

- Generated Stub 作为 Consumer 默认高性能路径。
- 0~4 参数 Generated CallSite。
- Generated Provider Dispatcher，缺失时 MethodHandle fallback。
- Vert.x 真实 HELLO / HELLO_ACK。
- Client/Server handshake timeout。
- Event Loop 本地 connection pending table 与 connection-local Request ID。
- 每 Endpoint 多连接分片。
- RpcFrameView 与 payload slice decode。
- Unary REQUEST/RESPONSE 协议快路径。
- ServiceDirectory 数组快照。
- 内置 P2C/EWMA 无候选 List 分配主路径。
- Byte Buddy Runtime Proxy 可选 fallback。
- Generated/JDK/Byte Buddy、协议 encode/decode、负载均衡路径 JMH 基准。

明确延期：

- Fory 固定 Type ID 与强制显式注册。必须先定义稳定 ID、冲突检测和滚动升级策略。
- 端到端 Buffer ownership / Buffer-oriented Codec。
- Generated Provider 参数链完全消除 Object[]。
- Provider execution policy 分层。

### V2-C：生态扩展

- Codec：Protobuf、Kryo、Hessian2、JSON。
- Registry：Nacos、ZooKeeper、Consul、Kubernetes EndpointSlice、Eureka。
- Protobuf/IDL 模式与跨语言兼容性测试。
- Registry Capability 契约测试矩阵。

## 4. 兼容性边界

V2-A 允许 Core API 在 0.x 阶段发生不兼容调整，但要求 Starter 使用方式保持稳定。V2-A 合并后，新增 Codec/Registry Adapter 应以“新增模块”为主，不应继续要求修改 Registry 与 Codec 核心抽象。

## 5. V2-A 验收标准

- 全 Maven Reactor `clean verify -Pquality` 通过。
- Javadoc warning 为 0。
- Registry discovery-only 模型与 Capability 一致性有单元测试。
- Registry namespace 与默认 Etcd key 空间兼容。
- Codec ID 重复、保留区非法使用有测试。
- Method-level Codec Binding 有测试。
- Handshake 能力协商包含成功、无共同 Codec、版本不兼容测试。
- Generated Client Processor 至少在 examples 中生成并被运行时优先发现。
- README 中英文同步，架构文档明确 Current / Next。


## 6. V2-B 验收标准

- 全 Reactor `clean verify -Pquality` 通过。
- Javadoc warning 为 0。
- 真实握手成功、Codec 不兼容与握手超时均有 Transport 级测试。
- connection-local Request ID 与连接分片有测试。
- Generated Client / Server 与运行时 Method ID 一致。
- Unary 协议快路径有语义等价测试。
- 默认 P2C/EWMA 使用数组快路径并保留旧 SPI 兼容入口。
- JMH 基准能够独立构建运行。
- README 中英文同步，架构/协议/性能文档不把延期能力写成已完成。
