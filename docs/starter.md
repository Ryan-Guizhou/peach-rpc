# Spring Boot Starter 与配置

## 1. 引入

业务项目只需要依赖：

```xml
<dependency>
    <groupId>io.peach.rpc</groupId>
    <artifactId>peach-rpc-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Starter 会带入默认 Fory、Vert.x、Etcd 和 CGLIB 适配器，默认仍使用 JDK Proxy 与内存 Registry。

## 2. Provider

```java
@PeachRpcService(interfaceClass = OrderService.class, version = "1.0.0")
public class OrderServiceImpl implements OrderService {
}
```

```yaml
peach:
  rpc:
    registry:
      type: etcd
      endpoints: http://127.0.0.1:2379
    server:
      enabled: true
      port: 19090
```

## 3. Consumer

```java
@PeachRpcReference(version = "1.0.0")
private OrderService orderService;
```

默认调用超时为 3 秒，可通过 `peach.rpc.client.timeout` 修改。

## 4. 主要配置

- `peach.rpc.enabled`：总开关。
- `peach.rpc.registry.type`：`memory` 或 `etcd`，也可以是自定义 SPI 名称。
- `peach.rpc.transport.type`：默认 `vertx`。
- `peach.rpc.client.proxy`：默认 `jdk`，可配置 `cglib`。
- `peach.rpc.client.load-balancer`：默认 `p2c-ewma`。
- `peach.rpc.server.max-concurrent`：Provider 最大并发业务执行数。
- `peach.rpc.transport.max-inflight-per-connection`：单连接最大未完成请求数。

## 5. Bean 覆盖

自动配置对 Registry、Codec Registry、TransportFactory、LoadBalancer、ProxyFactory、Client、Server 均使用 `@ConditionalOnMissingBean`，业务项目可以通过声明同类型 Bean 覆盖默认装配。
