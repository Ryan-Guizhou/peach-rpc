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

Provider 方法默认执行在虚拟线程。CPU 密集方法可以显式声明：

```java
@PeachRpcExecution(RpcExecutionMode.CPU)
Report buildReport();
```

极短且确定不阻塞的纯内存方法才可以使用 DIRECT：

```java
@PeachRpcExecution(RpcExecutionMode.DIRECT)
int healthScore();
```

DIRECT 默认禁止。只有配置 `peach.rpc.server.execution.allow-direct=true` 后才能注册包含 DIRECT 方法的服务，避免业务代码意外阻塞 Vert.x Event Loop。

## 3. Consumer

```java
@PeachRpcReference(version = "1.0.0")
private OrderService orderService;
```

默认调用超时为 3 秒，可通过 `peach.rpc.client.timeout` 修改。

自动重试默认最多 2 次 attempt，但**只有显式标注 `@PeachRpcIdempotent` 的服务方法才允许重试**。未标注方法无论 Retry Budget 是否有余额都不会由框架自动重试：

```java
@PeachRpcIdempotent
Order findById(Long id);
```

该注解表示业务方确认“相同参数重复执行不会产生不可接受的重复副作用”。创建订单、扣款、转账等接口不应仅为了获得重试而添加该注解，除非业务本身已有可靠幂等键/幂等语义。

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

编译器同时生成 `<Service>PeachRpcClientFactory` 与 `<Service>PeachRpcServerFactory`。Consumer `refer()` 优先使用 Generated Stub；Provider 注册服务时优先使用 Generated Dispatcher。缺失生成代码时分别回退到配置的 `ProxyFactory` 与 MethodHandle。

## 5. 主要配置

- `peach.rpc.enabled`：总开关。
- `peach.rpc.registry.type`：`memory` 或 `etcd`，也可以是自定义 SPI 名称。
- `peach.rpc.registry.namespace`：公共逻辑命名空间，默认 `default`；未来 Nacos/Kubernetes 等 Adapter 复用此语义。
- `peach.rpc.transport.type`：默认 `vertx`。
- `peach.rpc.transport.handshake-timeout`：协议握手超时，默认 3 秒。
- `peach.rpc.transport.connections-per-endpoint`：每个服务端点连接分片数，默认 1。
- `peach.rpc.client.proxy`：Generated Stub 缺失时的 fallback，默认 `jdk`；可显式选择 `cglib` 或可选 Byte Buddy 模块提供的 `bytebuddy`。
- `peach.rpc.client.load-balancer`：默认 `p2c-ewma`。
- `peach.rpc.server.max-concurrent`：Provider 最大并发业务执行数。
- `peach.rpc.server.drain-timeout`：Provider 关闭时等待 inflight 排空的最大时间，默认 30 秒。
- `peach.rpc.server.execution.allow-direct`：是否允许 DIRECT 方法运行在 Transport Event Loop，默认 false。
- `peach.rpc.server.execution.cpu-parallelism`：CPU 执行池线程数，默认当前 JVM 可用处理器数。
- `peach.rpc.server.execution.cpu-queue-capacity`：CPU 执行池有界队列容量，默认 1024。
- `peach.rpc.transport.max-inflight-per-connection`：单连接最大未完成请求数。
- `peach.rpc.client.resilience.max-attempts`：单次逻辑调用最大尝试次数，默认 2，包含首次调用。
- `peach.rpc.client.resilience.retry-budget-ratio`：每个原始请求补充的全局重试额度比例，默认 0.10。
- `peach.rpc.client.resilience.retry-budget-min-retries` / `retry-budget-max-retries`：重试预算突发下限/上限，默认 10/100。
- `peach.rpc.client.resilience.retry-base-backoff` / `retry-max-backoff`：随机退避窗口，默认 10ms/100ms。
- `peach.rpc.client.resilience.outlier-consecutive-failure-threshold`：Endpoint 连续基础设施失败剔除阈值，默认 5。
- `peach.rpc.client.resilience.outlier-ejection-duration`：Endpoint 临时剔除时间，默认 30 秒。
- `peach.rpc.client.resilience.circuit-consecutive-failure-threshold`：方法级熔断连续失败阈值，默认 20。
- `peach.rpc.client.resilience.circuit-open-duration`：Circuit OPEN 时间，默认 10 秒。

## 6. 可观测性 Observer

业务可以声明一个或多个 `RpcObserver` Bean。Starter 会在启动时组合这些 Observer，并同时注入 Consumer 与 Provider：

```java
@Bean
RpcObserver rpcObserver() {
    return new RpcObserver() {
        @Override
        public void onClientRetryScheduled(
                ServiceKey serviceKey,
                int methodId,
                int nextAttempt,
                long delayMillis,
                Throwable cause) {
            // Map to metrics / tracing / JFR.
        }
    };
}
```

Core 不依赖 Micrometer/OpenTelemetry。业务或后续 Adapter 负责把事件映射到具体观测系统。

## 7. Bean 覆盖

自动配置对 Registry、Codec Registry、TransportFactory、LoadBalancer、ProxyFactory、Client、Server 均使用 `@ConditionalOnMissingBean`，业务项目可以通过声明同类型 Bean 覆盖默认装配。
