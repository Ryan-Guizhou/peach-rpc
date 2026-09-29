# Peach RPC V2-C.2 连接与控制面高可用

> 状态：**Current（当前分支）**  
> V2-C.2 的目标不是继续扩展 Registry/Codec 数量，而是把“连接失效可检测、连接与控制面可恢复、恢复过程有边界且可验证”形成闭环。统一能力状态见 [Production Roadmap / Capability Matrix](production-roadmap.md)。

## 1. 本阶段结果

V2-C.2 当前已经完成：

1. HELLO/HELLO_ACK 协商式 PING/PONG Heartbeat；
2. Client/Server idle detection 与 heartbeat timeout；
3. request-driven single-flight reconnect；
4. exponential backoff + full jitter + 最大退避窗口；
5. connect/HELLO/REQUEST 共用逻辑 timeout budget；
6. rolling-compatible relative timeout metadata；
7. connection lifecycle RpcObserver；
8. Etcd compaction、restart 与 3 节点 leader transfer 恢复验证；
9. Nacos restart、Provider re-registration、Consumer re-subscribe；
10. last-known-good 数据面验证；
11. 独立 JVM Provider/Consumer recovery E2E；
12. 完整 Maven/质量/Chaos 门禁。

项目整体仍为 Preview。TLS/mTLS、标准可观测 Adapter、Wire Compatibility、完整性能矩阵、网络黑洞/长时间 soak 与容量/升级/回滚指南属于后续阶段。

## 2. Heartbeat 协商与 idle detection

Heartbeat 不是无条件控制帧，而是通过 HELLO/HELLO_ACK 协商出的 `RpcFeature.HEARTBEAT`。

~~~mermaid
sequenceDiagram
    participant C as Consumer
    participant P as Provider

    C->>P: TCP connect
    C->>P: HELLO(features includes HEARTBEAT)
    P->>C: HELLO_ACK(features includes HEARTBEAT)
    Note over C,P: HEARTBEAT negotiated
    Note over C,P: idle >= heartbeatInterval
    C->>P: PING
    P->>C: PONG
~~~

只有双方都支持 HEARTBEAT 才发送 PING/PONG，因此 N/N+1 滚动升级时不会把新控制帧发给旧节点。

连接状态：

~~~mermaid
stateDiagram-v2
    [*] --> Connecting
    Connecting --> Handshaking: TCP connected
    Handshaking --> Active: HELLO/ACK
    Handshaking --> Closed: timeout/rejected
    Active --> WaitingPong: idle / PING
    WaitingPong --> Active: any valid inbound frame
    WaitingPong --> Closed: heartbeat timeout
    Active --> Draining: GO_AWAY
    Draining --> Closed: inflight drained
    Active --> Closed: transport/protocol failure
~~~

语义：

- 正常 RPC 流量本身证明连接活跃，不额外发送 Heartbeat；
- 同一连接最多存在一个 outstanding PING；
- 任意有效入站帧都可清除 heartbeat wait；
- 超过 `heartbeatTimeout` 没有入站流量即摘除 silent/half-open connection；
- graceful drain 会停止 Heartbeat，避免排空阶段制造无意义控制流量。

默认配置：

| 配置 | 默认值 |
|---|---:|
| `peach.rpc.transport.heartbeat-interval` | `30s` |
| `peach.rpc.transport.heartbeat-timeout` | `10s` |
| `peach.rpc.transport.reconnect-base-backoff` | `50ms` |
| `peach.rpc.transport.reconnect-max-backoff` | `3s` |

## 3. Consumer 重连模型

每个 Endpoint connection shard 只允许一个 connecting future：

~~~text
request
  -> slot
     -> active connection -----------------------> request
     -> no connection
          -> shared connecting future
               -> optional full-jitter backoff
               -> TCP connect
               -> HELLO / ACK
               -> success: reset failure count
               -> failure: increase failure count
~~~

特点：

- 多个并发请求共享同一个连接创建 Future；
- 重连由业务请求触发，空闲 Consumer 不会永久主动重连；
- 连续失败按 exponential backoff 扩大窗口；
- 每次在窗口内使用 full jitter；
- 窗口有最大值；
- 握手成功后失败次数清零；
- graceful GO_AWAY / 本地主动关闭不计入异常 reconnect failure。

这避免大规模故障恢复时的 thundering herd。

## 4. 整体 Deadline 与 Relative Timeout Budget

V2-C.2 之后，一次 Consumer 逻辑调用的 Deadline 覆盖：

~~~text
service selection
 -> reconnect backoff
 -> TCP connect
 -> HELLO / HELLO_ACK
 -> REQUEST / RESPONSE
~~~

Transport 在连接/握手完成后只把**剩余预算**交给 Request。

Wire metadata 同时保留：

~~~text
deadlineEpochMillis=<legacy absolute deadline>
timeoutBudgetMillis=<relative remaining budget>
~~~

兼容策略：

- 新 Consumer 双写两个字段；
- 旧 Provider 忽略未知 relative budget，继续使用 absolute deadline；
- 新 Provider 检测到 relative budget 时避免依赖远端 wall clock；
- `timeoutBudgetMillis` 使用固定宽度编码，Transport 在真正 `socket.write` 前原地刷新，因此 Provider 看到的是更接近发送时的剩余预算；
- 不要求集群一次性升级所有节点。

Provider 业务执行阶段的取消仍主要由 Consumer timeout/Future cancel 通过 CANCEL 帧传播，不为每次 invocation 额外创建独立 timer。

## 5. Connection Lifecycle Observer

V2-C.2 扩展现有低依赖 `RpcObserver`，新增：

- `onConnectionEstablished`；
- `onConnectionReconnectScheduled`；
- `onConnectionHeartbeatTimeout`；
- `onConnectionClosed`。

连接角色：

- `CLIENT`；
- `SERVER`。

归一化关闭原因：

- `LOCAL_CLOSE`；
- `GO_AWAY`；
- `HEARTBEAT_TIMEOUT`；
- `TRANSPORT_ERROR`；
- `PROTOCOL_ERROR`；
- `REMOTE_CLOSE`。

默认 NOOP Observer 不创建事件对象。Spring 运行时把现有 Observer Bean 组合后，在 Client/Server 真正创建时通过 Transport options 注入，避免 AutoConfiguration Bean 循环。具体 Micrometer/OpenTelemetry/JFR Adapter 放在 V2-C.3。

## 6. Etcd 恢复闭环

### 6.1 Lease

正常主路径：

~~~text
grant lease
 -> keepAlive stream
 -> register keys with lease
~~~

恢复信号：

1. keepalive `onError` / `onCompleted`；
2. 低频 TTL watchdog 兜底检查当前 Lease；
3. 明确 `NOT_FOUND` 或 `TTL <= 0` 时进入同一单飞 recovery。

恢复实现还包含：

- grant 使用 jetcd 有界 timeout；
- `activeLeaseId` 校验，旧 Lease 的迟到回调不能 invalidate 新 Lease；
- recovery 重新发布当前 `activeRegistrations`；
- recovery 失败继续使用有界 exponential backoff + jitter。

### 6.2 Watch 与 compaction

Etcd Watch error 后不直接假设 revision 仍有效，而是：

~~~text
watch error
 -> backoff
 -> Range current snapshot
 -> publish snapshot
 -> Watch(snapshot.revision + 1)
~~~

集成测试真实执行：

1. 获取旧 revision；
2. 写入新实例推进 revision；
3. `KV.compact(currentRevision)`；
4. 从已压缩的 stale revision 建 Watch；
5. 验证 error 后重新 Range；
6. 验证新 Watch 获得当前完整实例视图。

### 6.3 Restart

jetcd Testcontainers 在容器重建后宿主机映射端口可能改变，因此 restart 集成测试使用 jetcd 官方同款 `cluster://<clusterName>` 动态 resolver，验证的是“逻辑 Etcd 目标重启”而不是固定某次 Testcontainers 动态端口。

生产配置仍继续使用用户配置的 Etcd endpoints；生产部署应保持 Registry DNS/VIP/固定地址语义。

### 6.4 Leader transfer

3 节点 leader transfer 不进入普通 Reactor，避免所有 PR 都承担多节点容器成本。独立 `Etcd Chaos` workflow：

- 创建 3 节点 Etcd；
- 定位当前 leader；
- 调用官方 `Maintenance.moveLeader(...)`；
- 验证 leader 发生变化；
- 验证 Registry 仍能注册新实例；
- 验证 Watch 继续得到完整快照；
- workflow 有 10 分钟硬上限；
- Etcd 相关 PR 自动触发，也支持手工触发。

## 7. Nacos restart 与 last-known-good

独立进程 E2E 使用真实 Nacos 3.2.4：

~~~text
Provider JVM ----\
                  -> Nacos
Consumer JVM ----/
~~~

验证序列：

1. Provider/Consumer JAR 独立启动并完成 RPC；
2. 保持 Consumer JVM 不重启，停止并重新启动 Provider，验证 Transport reconnect；
3. 重启 Nacos；
4. Nacos restart 期间既有 RPC 数据连接继续成功，证明数据面使用 last-known-good 本地目录；
5. 启动新的 Consumer，只有 Provider 恢复临时实例注册后才能发现服务，验证 Provider registration redo；
6. 把 Provider 从 19090 迁移到 19091；
7. 原 Consumer JVM 必须再次成功调用，证明 subscription redo 与新 Endpoint 快照生效。

E2E 为缩短 CI 故障恢复时间显式缩短 Circuit Breaker / Outlier Ejection 测试窗口，生产默认值保持不变。

## 8. Graceful Drain 与恢复语义

正常 Provider 关闭：

~~~text
Registry unregister
 -> GO_AWAY
 -> reject new requests
 -> wait inflight
 -> close
~~~

V2-C.2 明确区分：

- graceful GO_AWAY：正常排空，不增加 reconnect failure count；
- LOCAL_CLOSE：本地主动关闭；
- heartbeat/socket/protocol failure：异常连接关闭。

需要注意：Transport reconnect 成功不等于业务流量立即恢复。Circuit Breaker 与 Outlier Ejection 仍可能在自己的保护窗口内阻止流量重新进入，这是预期的 resilience 行为。

## 9. 自动化门禁

普通 PR：

~~~bash
python3 scripts/check_project.py
mvn -B -ntp clean verify -Pquality
~~~

CI 在 Reactor 后还会执行：

~~~bash
bash scripts/run_example_process_e2e.sh
~~~

在 CI 环境中该脚本自动包含 Nacos restart/re-registration/re-subscribe 流程。

Etcd 多节点 Chaos：

~~~bash
mvn -B -ntp -pl peach-rpc-registry-etcd -am test -Petcd-chaos
~~~

对应 workflow 仅在 Etcd 相关 PR 或手工触发时运行，并设置 10 分钟 job timeout。

当前分支已经通过：

- Repository checks；
- 完整 Maven Reactor + quality；
- Transport heartbeat/reconnect/observer tests；
- Etcd compaction/restart 集成测试；
- Etcd 3 节点 leader transfer Chaos；
- 独立 JVM Provider/Consumer recovery；
- Nacos restart + Provider re-registration + Consumer re-subscribe。

## 10. V2-C.2 完成门禁

- [x] Negotiated PING/PONG；
- [x] Client/Server idle detection；
- [x] Heartbeat timeout；
- [x] request-driven single-flight reconnect；
- [x] exponential backoff + full jitter；
- [x] connect/handshake/request 共享 timeout budget；
- [x] rolling-compatible relative timeout metadata；
- [x] connection/recovery RpcObserver events；
- [x] Etcd compaction recovery；
- [x] Etcd restart registration/watch recovery；
- [x] Etcd 3-node leader transfer Chaos；
- [x] Nacos restart/re-registration/re-subscribe；
- [x] last-known-good data-plane behavior；
- [x] independent JVM Provider/Consumer recovery E2E；
- [x] full Reactor / quality / CI gate。

## 11. 后续阶段边界

以下能力继续重要，但不再属于 V2-C.2 未完成项。

### V2-C.3

- TLS / mTLS；
- certificate lifecycle；
- Micrometer Adapter；
- OpenTelemetry Adapter；
- JFR Adapter；
- Registry-specific recovery metrics/events；
- auth-enabled Nacos 与凭据错误脱敏。

### V2-D / V2-E

- 完整 payload/concurrency/connection performance matrix；
- Buffer ownership / allocation second pass；
- Fory Stable Type ID；
- Schema fingerprint；
- N/N+1 rolling compatibility；
- rollback compatibility；
- Protocol/Codec compatibility matrix。

### 持续 Robustness

- 网络黑洞/partition；
- 长时间 Registry/Transport recovery soak；
- malformed frame/fuzz/property tests；
- 多实例滚动发布矩阵；
- 容量规划与升级/回滚 runbook。
