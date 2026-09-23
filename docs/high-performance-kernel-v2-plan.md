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

### V2-A：Core 契约稳定化（本 PR）

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

### V2-B：热路径替换

- Generated Stub 成为默认 Consumer 路径。
- Generated Dispatcher 替换 Provider MethodHandle 默认分派。
- Vert.x 连接建立阶段真正执行 HELLO / HELLO_ACK。
- 增加 Byte Buddy Runtime Stub fallback。
- Fory 类型扫描、显式注册与固定 Type ID。
- Event Loop 亲和连接组与 connection-local pending table。

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
