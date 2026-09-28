# Peach RPC V2-B.2 高可用收口

## 1. 目标

V2-B.2 不扩展新的 Codec/Registry 生态，而是优先修复真实生产故障中会造成服务消失、停机卡死或示例不可运行的问题。

本批次范围：

1. 将 V2-B.1 第二批正确纳入当前 main 开发基线；
2. Etcd Lease 自恢复；
3. Watch/Recovery 退避治理；
4. Provider 控制面超时；
5. Spring Starter 服务扫描语义；
6. examples 完整启动与真实 RPC 烟测。

## 2. Etcd Lease 自恢复

Provider 成功注册的实例会进入本地 active registration 集合。

```text
register success
    |
    v
active registrations
    |
keepalive lost
    |
    v
invalidate old lease
    |
    v
backoff + jitter
    |
    v
grant new lease
    |
    v
republish active registrations
```

恢复过程只影响 Registry 控制面，请求数据面仍然读取本地 ServiceDirectory。

恢复调度有单飞保护，避免同一个进程同时创建多条重复恢复链。

## 3. Watch 重订阅退避

Watch/Range 失败不再固定每 1 秒同时重试。

当前采用：

- exponential backoff；
- jitter；
- 最大 30 秒窗口；
- 成功 Range 后重置 retry attempt。

这样可以降低 Etcd 恢复时大量 Consumer 同步重连产生的 thundering herd。

## 4. Provider 控制面超时

Provider start/rollback/close 期间的 Registry 操作不能无限等待。

新增默认 3 秒 control-plane timeout，用于：

- 服务注册；
- 注册失败后的 rollback；
- 正常停机 unregister。

Transport Graceful Drain 继续使用独立 drain timeout，两类超时语义不混用。

## 5. Spring 服务扫描

`@PeachRpcService` 现在同时是 Spring stereotype。

因此：

```java
@PeachRpcService(interfaceClass = GreetingService.class, version = "1.0.0")
public class GreetingServiceImpl implements GreetingService {
}
```

无需再额外添加 `@Component`。

该缺口由 examples 完整启动烟测发现：此前 Provider 虽然成功监听端口，但服务实现从未成为 Spring Bean，因此 Registry 中没有可调用实例。

## 6. Examples 启动门禁

`ExampleApplicationSmokeTest` 会启动完整 Spring Boot Context，并验证：

```text
Spring Boot
  -> @PeachRpcService scan
  -> PeachRpcServer start
  -> Memory Registry publish
  -> @PeachRpcReference injection
  -> Generated Consumer Stub
  -> Vert.x TCP
  -> Fory
  -> Generated Provider Dispatcher
  -> GreetingServiceImpl
  -> Hello, Peach RPC!
```

测试使用临时空闲端口，减少 CI 与开发环境端口冲突。

同时 examples 使用 Spring Boot Maven repackage，可直接 `java -jar` 运行。

## 7. 本批次仍不包含

- PING/PONG heartbeat；
- TLS/mTLS；
- relative deadline budget；
- Micrometer/OpenTelemetry/JFR Adapter；
- Buffer ownership；
- Fory Stable Type ID / Schema fingerprint；
- Streaming RPC。

这些继续保留在生产就绪门禁中。
