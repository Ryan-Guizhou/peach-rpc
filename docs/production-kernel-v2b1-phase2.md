# Peach RPC V2-B.1 生产内核第二批

## 1. 范围

第二批继续补齐“长期稳定运行”能力，不新增 Registry/Codec 生态：

1. Provider Execution Policy；
2. 低依赖 Observability 契约；
3. Etcd 真实环境集成测试。

TLS/mTLS、Micrometer/OpenTelemetry/JFR Adapter、Buffer ownership、Fory 稳定 Type ID 和 Streaming 不属于本批次。

## 2. Provider Execution Policy

方法级执行模式通过 `@PeachRpcExecution` 声明。

### BLOCKING_VIRTUAL

默认模式。适合 JDBC、文件、同步 HTTP/SDK 等阻塞型业务。

```text
Event Loop
  -> admission
  -> virtual thread
  -> business method
```

### CPU

CPU 密集方法进入有界固定线程池：

```text
Event Loop
  -> admission
  -> bounded CPU queue
  -> fixed CPU worker
```

队列满时直接返回 `OVERLOADED`，避免无界排队造成尾延迟和内存膨胀。

### DIRECT

直接在 Transport Event Loop 执行，仅允许极短且确定不阻塞的逻辑。默认 `allowDirect=false`；注册包含 DIRECT 方法的服务会 fail-fast，除非 Provider 显式开启。

该设计把 DIRECT 定位为高级逃生口，而不是默认性能优化手段。

## 3. RpcObserver

Core 新增 `RpcObserver`，没有 Micrometer、OpenTelemetry 或 JFR 依赖。

当前生命周期事件：

- Consumer attempt completed；
- Consumer retry scheduled；
- Provider invocation completed。

默认 NOOP Observer 的 `enabled=false`，主链不会构造通用 Event 对象。多个 Observer 在启动阶段组合；单个 Observer 抛出 RuntimeException 时会被隔离。

这为后续提供稳定映射点：

```text
RpcObserver
  -> Micrometer Adapter
  -> OpenTelemetry Adapter
  -> JFR Adapter
```

具体第三方 Adapter 仍属于下一阶段。

## 4. Etcd 真实集成测试

Etcd Adapter 使用与项目 jetcd 版本一致的官方 `jetcd-test` 测试设施，在真实 Etcd 进程上验证：

- register -> lookup -> unregister；
- Range + Watch 快照刷新；
- namespace 隔离；
- Lease keepalive 停止后注册信息过期。

这些测试进入 Maven Reactor，因此 Registry Adapter 不再只有“代码看起来正确”的静态保证。

当前仍未完成：

- compaction 导致 Watch revision 失效后的专项验证；
- 网络断链/恢复故障注入；
- 多节点 leader 切换。

这些仍在 production readiness 中保留，不在本批次宣称完成。

## 5. 安全默认值

- BLOCKING_VIRTUAL 为默认执行模式；
- DIRECT 默认关闭；
- CPU queue 有界；
- Observer 默认关闭；
- Observer 异常不能破坏 RPC；
- Etcd 仍只位于控制面，不进入请求热路径。

## 6. 后续

建议下一批优先级：

1. Micrometer + OpenTelemetry Adapter；
2. Etcd compaction / disconnect / leader-change 故障注入；
3. TLS/mTLS；
4. 根据端到端基准决定 Buffer ownership API 改造范围；
5. Fory stable Type ID / Schema fingerprint。
