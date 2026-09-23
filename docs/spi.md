# SPI 扩展指南

Peach RPC 的 SPI 用于隔离可以独立替换的实现技术和策略，不用于普通工具类。

## 1. 基本规则

1. 扩展接口不能暴露第三方实现类型。
2. 扩展在启动阶段解析并缓存，禁止每次 RPC 动态发现。
3. 扩展名称属于稳定配置契约。
4. 重复扩展名直接失败，不依赖 Classpath 顺序。
5. 有状态网络资源通过 Factory SPI 创建。
6. SPI 只负责实现替换；能够预绑定到 Service/Method/Connection 的信息应尽早绑定。

## 2. 当前 SPI

- `RpcCodec`
- `RegistryFactory`
- `RpcTransportFactory`
- `LoadBalancer`
- `ProxyFactory`

## 3. Codec：SPI 与热路径分离

`RpcCodec` 是启动阶段扩展点，但高性能路径使用：

```java
RpcMethodCodec bind(RpcMethodDescriptor descriptor);
```

返回的 `RpcMethodCodec` 在方法粒度持有 encoder/decoder。

因此未来 Protobuf、Fory 或自定义 Codec 可以在 `bind()` 阶段创建方法专用状态，而单次请求无需重新解析 `Method`、参数类型或 Schema。

Codec ID 属于线协议 ABI，不根据 SPI 顺序动态分配。

## 4. Registry：能力不是最低公共分母

`Registry` 提供生命周期和 `ServiceDiscovery`，主动注册通过可选 `ServiceRegistrar` 表达。

```java
Registry registry = ...;

ServiceDiscovery discovery = registry;
Optional<ServiceRegistrar> registrar = registry.registrar();
RegistryCapabilities capabilities = registry.capabilities();
```

这允许 Kubernetes 等实现保持 discovery-only，而不需要用空方法伪装支持注册。

当前 Capability 包括：

- REGISTRATION
- SUBSCRIPTION
- REVISION
- LEASE
- HEALTH
- WEIGHT
- ZONE
- CLUSTER
- METADATA

Adapter 只能声明实际具备的能力。

## 5. RegistryOptions

`RegistryFactory` 不再接收裸 `Map<String, String>`。公共配置使用强类型：

```java
RegistryOptions(
    List<String> endpoints,
    String namespace,
    Map<String, String> providerOptions
)
```

其中 endpoints 与 namespace 是跨注册中心公共语义；只有 Etcd Lease TTL、Consul Datacenter 等厂商特有参数才进入 `providerOptions`。这样新增 Nacos/Kubernetes Adapter 时不需要继续向 Core Factory 接口堆字符串键。

## 5. Proxy 与 Generated Stub

`ProxyFactory` 是兼容 fallback，不是长期默认性能路径。

标注 `@PeachRpcContract` 并启用 `peach-rpc-codegen` 后，编译期生成 Consumer Stub；运行时优先发现生成类，缺失时才回退 ProxyFactory。

后续 V2-B 会加入 Byte Buddy fallback。CGLIB 保留兼容定位，不作为高性能主线。
