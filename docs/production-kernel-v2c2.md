# Peach RPC V2-C.2 连接与控制面高可用

> 状态：**Partial（当前开发分支）**  
> 本文记录 V2-C.2 的当前实现、边界和剩余生产门禁。统一能力状态见 [Production Roadmap / Capability Matrix](production-roadmap.md)。

## 1. 目标

V2-C.2 不扩展新的 Codec 或 Registry 生态，重点解决 RPC 长连接与控制面故障恢复中的生产级高可用问题：

1. 空闲长连接能够主动发现 silent/half-open connection；
2. 异常断链后的连接恢复有单飞、有界退避和 jitter，避免重连风暴；
3. 单次逻辑调用的 timeout 覆盖 connect、HELLO/ACK 与 REQUEST，而不是仅覆盖已建立连接后的网络请求；
4. Deadline 传播减少对跨节点 wall clock 一致性的依赖；
5. Registry 重启、断链、compaction 等故障能够通过自动化测试证明恢复行为；
6. 独立进程 E2E 与恢复事件可观测性形成最终闭环。

当前分支已完成前四项的主要运行时代码和 Transport 自动化测试。Registry Chaos、独立进程 E2E 与连接/恢复 Observer 仍待完成。

## 2. Heartbeat 能力协商

PING/PONG 不是无条件启用的控制帧，而是通过 HELLO / HELLO_ACK 协商出的 `RpcFeature.HEARTBEAT`。

~~~mermaid
sequenceDiagram
    participant C as Consumer
    participant P as Provider

    C->>P: TCP connect
    C->>P: HELLO(features includes HEARTBEAT)
    P->>C: HELLO_ACK(features includes HEARTBEAT)
    Note over C,P: HEARTBEAT negotiated
    Note over C,P: connection becomes Active
    C->>P: PING after idle interval
    P->>C: PONG
~~~

只有双方都声明 HEARTBEAT 后才允许发送 PING/PONG。这样在滚动升级期间，新节点不会向不理解 Heartbeat 的旧节点发送新的控制帧。

## 3. Idle detection

Client 和 Server 都维护连接本地的最近入站活跃时间。

当前语义：

- 握手完成且协商 HEARTBEAT 后启动周期检查；
- 连接持续有正常 REQUEST / RESPONSE / CANCEL / GO_AWAY / PING / PONG 流量时，不额外发送 PING；
- 入站空闲达到 `heartbeatInterval` 后发送一个 PING；
- 同一连接最多存在一个 outstanding PING；
- PING 发出后，任意有效入站帧都能证明远端仍然存活并清除 heartbeat wait；
- 超过 `heartbeatTimeout` 没有入站流量，则连接被判定不可用并关闭；
- graceful drain 会停止 Heartbeat，避免排空阶段产生无意义控制流量。

~~~mermaid
stateDiagram-v2
    [*] --> Active
    Active --> Active: normal inbound traffic
    Active --> WaitingPong: idle >= heartbeatInterval / send PING
    WaitingPong --> Active: any valid inbound frame
    WaitingPong --> Closed: heartbeatTimeout
    Active --> Draining: GO_AWAY / local drain
    WaitingPong --> Draining: GO_AWAY / local drain
    Draining --> Closed: inflight drained
~~~

该机制用于补充 TCP keepalive。TCP keepalive 仍启用，但 Peach RPC 不依赖操作系统默认 keepalive 周期来满足应用级故障发现时间。

## 4. Consumer 重连

Consumer 仍采用按 Endpoint、按 connection shard 管理连接槽。

每个槽位保证：

- 同一时刻只存在一个连接创建 Future；
- 多个并发请求共享该 Future，不重复创建连接；
- 已成功握手的连接发生异常后记录连续失败次数；
- 下一次业务请求触发连接重建；
- 重连退避使用 exponential backoff + full jitter；
- 退避窗口有最大值；
- 握手成功后失败次数归零。

~~~text
request
  -> slot
     -> active connection --------------------------> request
     -> no connection
          -> one shared connecting future
               -> optional jitter backoff
               -> TCP connect
               -> HELLO / ACK
               -> success: reset failures
               -> failure: increment failures
~~~

当前默认值：

| 配置 | 默认值 |
|---|---:|
| `peach.rpc.transport.heartbeat-interval` | `30s` |
| `peach.rpc.transport.heartbeat-timeout` | `10s` |
| `peach.rpc.transport.reconnect-base-backoff` | `50ms` |
| `peach.rpc.transport.reconnect-max-backoff` | `3s` |

重连是 **request-driven**，不会在没有业务流量时主动永久重连。这样可以避免空闲 Consumer 对已经下线的 Endpoint 持续制造连接流量。

## 5. 整体 timeout budget

V2-C.1 之前，Core 虽然维护逻辑 Deadline，但 Transport 在连接建立完成后仍然拿到完整 timeout，因此 connect / HELLO / ACK 耗时不会从网络请求预算扣除。

V2-C.2 改为：

~~~text
logical deadline
    |
    +-- service selection
    +-- reconnect backoff
    +-- TCP connect
    +-- HELLO / HELLO_ACK
    +-- REQUEST / RESPONSE
~~~

`VertxRpcTransportClient` 在进入连接获取前记录 monotonic deadline；连接和握手完成后只把剩余时间交给具体 Request。

因此慢建连不能再额外突破一次调用的逻辑 timeout。

## 6. Relative timeout budget

仅传播绝对 `deadlineEpochMillis` 会受到 Consumer/Provider wall clock 偏差影响。

V2-C.2 使用兼容式双写：

~~~text
deadlineEpochMillis=<absolute epoch millis>
timeoutBudgetMillis=<remaining budget millis>
~~~

语义：

- 新 Consumer 同时写两个字段；
- 新 Provider 检测到 `timeoutBudgetMillis` 时，不再因为本机 wall clock 与 Consumer 不一致而用绝对 deadline 提前拒绝请求；
- 旧 Provider 忽略未知 metadata，并继续使用 `deadlineEpochMillis`；
- 旧 Consumer 没有 relative budget 时，新 Provider 继续保持旧 absolute deadline 行为。

这允许 N/N+1 滚动升级，而不要求一次性同时升级所有节点。

### 当前边界

relative budget 当前主要解决 **跨节点 wall-clock 判定偏差**。

Provider 对已经进入业务执行阶段的硬超时仍主要由：

~~~text
Consumer timeout
 -> CANCEL
 -> Provider connection inflight
 -> Future.cancel
 -> interrupt cancellable execution
~~~

完成。

本阶段不额外在 Provider 为每个 invocation 建立独立 timeout timer，避免在没有基准和完整取消语义验证前增加每请求定时器成本。

## 7. 与 Graceful Drain 的关系

Heartbeat failure 与正常 drain 必须区分：

- Heartbeat timeout / socket exception：异常连接；
- Provider 正常关闭：Registry unregister -> GO_AWAY(UNAVAILABLE) -> inflight drain；
- 收到 graceful GO_AWAY 后 Client 释放该连接槽，但不把它视为异常 reconnect failure；
- 新请求可以通过同一 Endpoint 的连接槽建立新连接，但 Registry 正常情况下已经先移除正在关闭的 Provider。

这避免部署排空被错误统计为故障，并减少不必要的 reconnect backoff。

## 8. 当前自动化测试

当前分支已经增加：

### Protocol

- PING 编码/解码；
- PONG 编码/解码；
- 非 PING/PONG 类型不能通过 heartbeat encoder；
- absolute deadline + relative timeout budget 双写/解析。

### Vert.x Transport

- 正常 Client/Server handshake 与 request；
- 空闲时间超过多个 heartbeat interval 后连接仍保持可用；
- Provider 停止并重新监听相同 Endpoint 后，Consumer 能重新建立连接并恢复调用；
- 原有 handshake timeout、CANCEL、connection shard、Graceful Drain 测试继续保留。

CI 仍以：

~~~bash
python3 scripts/check_project.py
mvn -B -ntp clean verify -Pquality
~~~

作为基础门禁。

## 9. V2-C.2 尚未完成

### 9.1 Etcd Chaos

仍需真实验证：

- compaction 后 Range + Watch 恢复；
- 网络断链 / 恢复；
- Etcd 进程重启；
- 多节点 leader change；
- 恢复期间 last-known-good ServiceDirectory 行为。

### 9.2 Nacos Chaos

仍需真实验证：

- Nacos Server restart；
- Provider ephemeral registration redo；
- Consumer subscription redo；
- 重启期间 last-known-good snapshot；
- auth-enabled 场景错误脱敏。

### 9.3 独立进程 E2E

当前 Nacos RPC round-trip 使用两个独立 Spring Context，但仍处于同一个测试 JVM。

V2-C.2 还需要建立真正的：

~~~text
Provider JVM
   |
  Nacos
   |
Consumer JVM
~~~

进程级测试，验证启动、调用、Provider stop/restart、Consumer 恢复与退出码。

### 9.4 Recovery observability

当前 `RpcObserver` 主要覆盖：

- Client attempt；
- Retry；
- Provider invocation。

连接建立、heartbeat timeout、reconnect 和 Registry recovery 生命周期事件仍待增加。具体 Micrometer/OpenTelemetry/JFR Adapter 属于 V2-C.3。

## 10. V2-C.2 完成门禁

全部满足后才能把 V2-C.2 从 Partial 改为 Current：

- [x] Negotiated PING/PONG；
- [x] Client/Server idle detection；
- [x] Heartbeat timeout；
- [x] request-driven single-flight reconnect；
- [x] exponential backoff + full jitter；
- [x] connect/handshake/request 共享 timeout budget；
- [x] rolling-compatible relative timeout metadata；
- [ ] Etcd compaction/disconnect/restart/leader-change fault injection；
- [ ] Nacos restart/re-registration/re-subscribe fault injection；
- [ ] last-known-good 行为验证；
- [ ] 独立 JVM Provider/Consumer recovery E2E；
- [ ] connection/recovery Observer events；
- [ ] 全 Reactor 与 CI 最终通过。
