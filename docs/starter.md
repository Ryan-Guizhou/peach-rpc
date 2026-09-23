# Spring Boot Starter 与配置

## 1. 引入

业务项目基础使用只需要依赖：

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
      namespace: default
    server:
      enabled: true
      port: 19090
```

Provider 启用时，当前 Registry 必须暴露 `ServiceRegistrar`。如果配置的是 discovery-only Registry，自动配置会 fail-fast。

## 3. Consumer

```java
@PeachRpcReference(version = "1.0.0")
private OrderService orderService;
```

默认调用超时为 3 秒，可通过 `peach.rpc.client.timeout` 修改。

## 4. 可选：编译期 Consumer Stub

普通 Starter 不要求代码生成，因此现有业务可继续使用 JDK Proxy fallback。

需要高性能调用路径时，服务接口标注：

```java
@PeachRpcContract
public interface OrderService {
    Order findById(Long id);
}
```

并在业务模块 Maven Compiler 中配置：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>io.peach.rpc</groupId>
                <artifactId>peach-rpc-codegen</artifactId>
                <version>${peach-rpc.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

编译器生成 `<Service>PeachRpcClientFactory`。Consumer `refer()` 会优先发现生成 Factory，找不到时再回退到配置的 `ProxyFactory`。

当前 V2-A 只生成 Consumer Stub；Generated Server Dispatcher 属于 V2-B。

## 5. 主要配置

- `peach.rpc.enabled`：总开关。
- `peach.rpc.registry.type`：`memory` 或 `etcd`，也可以是自定义 SPI 名称。
- `peach.rpc.registry.namespace`：公共逻辑命名空间，默认 `default`；未来 Nacos/Kubernetes 等 Adapter 复用此语义。
- `peach.rpc.transport.type`：默认 `vertx`。
- `peach.rpc.client.proxy`：Generated Stub 缺失时的 fallback，默认 `jdk`。
- `peach.rpc.client.load-balancer`：默认 `p2c-ewma`。
- `peach.rpc.server.max-concurrent`：Provider 最大并发业务执行数。
- `peach.rpc.transport.max-inflight-per-connection`：单连接最大未完成请求数。

## 6. Bean 覆盖

自动配置对 Registry、Codec Registry、TransportFactory、LoadBalancer、ProxyFactory、Client、Server 均使用 `@ConditionalOnMissingBean`，业务项目可以通过声明同类型 Bean 覆盖默认装配。
