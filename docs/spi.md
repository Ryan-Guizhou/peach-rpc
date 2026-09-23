# SPI 扩展指南

Peach RPC 的 SPI 用于隔离可独立替换的技术与策略，不用于普通工具类。

## 规则

1. 扩展接口不能暴露第三方实现类型。
2. 扩展在启动阶段解析并缓存。
3. 扩展名称属于稳定配置契约。
4. 重复扩展名直接失败，不依赖 Classpath 顺序。
5. 有状态网络资源通过 Factory SPI 创建，不把可变连接状态放到全局策略单例。

## 示例

```java
@SPI("p2c-ewma")
public interface LoadBalancer {
    ServiceInstance select(List<LoadBalanceContext> candidates);
}
```

实现类使用 `@Extension("custom")`，并在 `META-INF/services/<接口全名>` 中注册。

当前扩展点：Codec、RegistryFactory、TransportFactory、LoadBalancer、ProxyFactory。
