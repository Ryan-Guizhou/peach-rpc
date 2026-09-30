# Nacos Registry Adapter

## 1. 定位

`peach-rpc-registry-nacos` 将 Nacos NamingService 适配为 Peach RPC 的 `Registry` / `ServiceRegistrar`。它只属于控制面：Consumer 单次 RPC 热路径读取 Core 的本地 `ServiceDirectory`，不会访问 Nacos。

当前基线使用 Nacos Client 3.2.4。

## 2. 坐标映射

| Peach RPC | Nacos | 说明 |
|---|---|---|
| `RegistryOptions.namespace` | namespaceId | 空值时使用 `public` |
| `registry.nacos.group` | groupName | 默认 `PEACH_RPC` |
| `ServiceKey.canonicalName()` | serviceName | 接口名、版本和 RPC group 组成稳定身份 |
| `registry.nacos.cluster` | clusterName | 默认 `DEFAULT` |
| `RpcEndpoint` | Instance ip/port | 必须是 Consumer 可路由地址 |
| `ServiceInstance.weight` | Instance weight | Core 100 对应 Nacos 1.0 |
| `ServiceInstance.metadata` | Instance metadata | 用户键与框架保留键共同发布 |

Nacos group 与 RPC `ServiceKey.group` 不可混用：前者是 Registry 管理分组，后者属于 RPC 服务身份。

## 3. 保留元数据

框架保留 `peach.rpc.*` 前缀，当前包括：

- `peach.rpc.instance-id`
- `peach.rpc.interface`
- `peach.rpc.version`
- `peach.rpc.group`
- `peach.rpc.protocol`
- `peach.rpc.cluster`

Provider 用户元数据覆盖该前缀会在注册前失败。

## 4. 查询与订阅

`lookup(ServiceKey)` 只查询指定服务、group 和 cluster，不扫描全量服务。

订阅流程：

1. 使用固定 EventListener 注册订阅；
2. 拉取当前完整实例视图作为 initial snapshot；
3. 在 initial snapshot 期间到达的 NamingEvent 先缓存；
4. 后续事件进入单订阅串行队列；
5. healthy=false、enabled=false、权重非正或端点非法的实例被过滤；
6. 实例按 endpoint 与 instanceId 稳定排序并去重；
7. 相同视图不重复刷新 Core；
8. 变化视图使用 Adapter 进程内 AtomicLong 生成单调 revision；
9. 同一 Registry Client 主动注销本地 Provider 后，会立即从该 Client 的 subscription 快照中移除对应 endpoint，避免最后一个实例注销时目录悬挂；
10. 远端 Provider 生命周期仍以 Nacos NamingEvent 为主通道，并由独立 Provider/Consumer Client 集成测试验证；
11. 关闭时使用原 EventListener 实例 unsubscribe。

## 5. 线程与资源

Nacos Java SDK 的注册、注销、查询和订阅初始化可能阻塞。Adapter 使用名称以 `peach-rpc-nacos-control-` 开头的私有有界 ThreadPoolExecutor：

- core threads：2；
- max threads：4；
- queue：256；
- 拒绝时快速失败；
- 不使用 ForkJoinPool.commonPool；
- 不占用 Vert.x Event Loop。

Registry 关闭先取消订阅，再关闭 NamingService，最后关闭控制面执行器；重复关闭幂等。

## 6. 配置

```yaml
peach:
  rpc:
    registry:
      type: nacos
      endpoints: 127.0.0.1:8848
      namespace: public
      nacos:
        group: PEACH_RPC
        cluster: DEFAULT
        username: ${NACOS_USERNAME:}
        password: ${NACOS_PASSWORD:}
```

用户名和密码不会进入 Core 类型。错误日志不得输出 password。

## 7. Provider 发布地址

监听地址与注册中心发布地址是不同概念：

```yaml
peach:
  rpc:
    server:
      host: 0.0.0.0
      port: 19090
      advertised-host: 10.0.0.15
      advertised-port: 19090
```

当 host 为 `0.0.0.0`、`::` 或 `[::]` 且没有 advertised-host 时，Provider 启动失败，避免把不可路由地址写入 Nacos。advertised-port=0 时使用 Transport 实际监听端口。

## 8. 测试

CI 启动固定版本 Nacos 3.2.4，并设置 `NACOS_TEST_ENDPOINT`。当前自动化覆盖 SPI 加载、服务名映射、权重与元数据映射、健康实例过滤、共享 Registry Contract、独立 Provider/Consumer Client 的远端注册/注销订阅收敛、真实注册/查询/订阅/注销，以及拆分 Provider/Consumer 的 RPC round-trip。独立 Nacos Chaos 使用 Consumer、Provider A、Provider B 三个 Registry Client 模拟容器 pause/unpause 下的注册与订阅恢复。

本地未提供 `NACOS_TEST_ENDPOINT` 时，真实 Nacos 集成测试会跳过；普通单元测试仍正常执行。
