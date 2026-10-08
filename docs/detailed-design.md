# Peach RPC 1.0 详细设计

> 状态：**Current / 1.0.0 GA**

## 1. Consumer 调用链

```mermaid
sequenceDiagram
    participant A as Application
    participant C as PeachRpcClient
    participant D as ServiceDirectory
    participant L as P2C/EWMA
    participant T as Vertx Transport
    participant P as Provider

    A->>C: invoke(method,args)
    C->>C: create logical deadline / trace context
    C->>D: read immutable snapshot
    D-->>C: compatible instances[]
    C->>L: choose(instances, endpoint stats)
    L-->>C: endpoint
    C->>C: circuit/outlier/retry decision
    C->>T: encode + request(endpoint)
    T->>P: REQUEST
    P-->>T: RESPONSE / ERROR
    T-->>C: complete attempt
    C-->>A: result / exception
```

关键约束：

- `ServiceDirectory` 读取不触发 Registry I/O；
- 单次 logical call 可以包含多个 Attempt；
- Timeout 结束后不允许 Retry 越过 Deadline；
- CANCEL 可传播到 Provider。

## 2. Provider 请求处理

```mermaid
flowchart TD
    Frame[Inbound Frame] --> Validate[Protocol Validation]
    Validate --> Track[Connection-local Inflight]
    Track --> Admission[Provider Admission]
    Admission --> Bind[Method Binding]
    Bind --> Decode[Payload Decode]
    Decode --> Policy{Execution Mode}
    Policy -->|BLOCKING_VIRTUAL| VT[Virtual Thread]
    Policy -->|CPU| CPU[Bounded CPU Pool]
    Policy -->|DIRECT allowed| EL[Event Loop]
    VT --> Invoke[Generated Dispatcher / Fallback]
    CPU --> Invoke
    EL --> Invoke
    Invoke --> Async{CompletionStage?}
    Async -->|No| Encode[Response Encode]
    Async -->|Yes| Await[Register Completion Callback]
    Await --> ReturnExec[Dispatch Back To Provider Executor]
    ReturnExec --> Encode
    Encode --> Write[Transport Write]
```

### Admission

Provider 最大并发必须有边界。CPU 模式还具有独立有界队列；满时返回 OVERLOADED。

对于业务方法返回的 `CompletionStage`，Provider 不在 CPU/Virtual Thread worker 上执行 `join()`。框架注册完成回调后立即归还执行 worker，但 **admission permit 会一直持有到异步业务真正完成、失败或取消**，因此异步化不会绕过 Provider 最大业务并发保护。

异步 Stage 若稍后在业务线程、Netty/Vert.x EventLoop 或其他执行器上完成，框架不会直接在该线程执行响应序列化，而是重新调度到 Provider 管理的执行资源，并恢复 Trace/Metadata Scope 后再编码响应。CPU 方法回到有界 CPU Pool；异步 DIRECT 完成也通过 CPU Pool 隔离，避免业务 Future 的完成线程承担不可控的编码工作。若有界 CPU Pool 连异步完成任务也无法接收，当前 RPC fail-fast 为 `OVERLOADED` 并释放 admission，而不是回退到外部 completion thread 执行编码。正常完成路径也遵循“先完成响应编码与 admission 释放，再对外完成响应 Future”的顺序，避免调用方刚收到响应就因资源计数尚未归还而看到瞬时 `OVERLOADED`。

### Cancellation

Transport 根据 Request ID 找到 connection-local inflight Future，Core 再向业务执行任务传播取消。

## 3. Registry 与本地目录

```mermaid
sequenceDiagram
    participant P as Provider
    participant R as Registry Adapter
    participant B as Etcd/Nacos
    participant C as Consumer Adapter
    participant D as ServiceDirectory

    P->>R: register(instance)
    R->>B: register
    C->>B: subscribe(service)
    B-->>C: snapshot/change
    C->>C: normalize + revision
    C->>D: RegistrySnapshot
    D->>D: compatibility filter
    D->>D: replace immutable array snapshot
    P->>R: unregister(instance)
    R->>B: unregister
    B-->>C: empty/updated snapshot
    C->>D: converge directory
```

### Etcd

- Lease 作为 Provider 生命周期；
- Watch 出错后重新 Range；
- compaction 后从有效 revision 恢复；
- keepalive + TTL watchdog。

### Nacos

- Provider 使用临时实例；
- SDK 阻塞调用在 Adapter 控制面执行器；
- 本地主动 unregister 后同步收敛当前进程 subscription；
- 远端 Provider 生命周期仍以 NamingEvent 为主通道。

## 4. 连接状态机

```mermaid
stateDiagram-v2
    [*] --> Connecting
    Connecting --> TLSHandshake: secure mode
    Connecting --> Hello: plaintext
    TLSHandshake --> Hello: verified
    TLSHandshake --> Closed: failure
    Hello --> Active: HELLO_ACK
    Hello --> Closed: timeout / reject
    Active --> HeartbeatWait: idle
    HeartbeatWait --> Active: PONG / inbound
    HeartbeatWait --> Closed: timeout
    Active --> Draining: GO_AWAY
    Draining --> Closed: inflight drained
    Active --> Closed: fatal error
```

## 5. Retry 时序

```mermaid
flowchart TD
    Failure[Attempt Failure] --> Retryable{Idempotent and Retryable?}
    Retryable -->|No| Final[Complete Failure]
    Retryable -->|Yes| Attempts{Max Attempts?}
    Attempts -->|Exceeded| Max[MAX_ATTEMPTS]
    Attempts -->|Available| Budget{Retry Budget?}
    Budget -->|No| BUDGET[BUDGET]
    Budget -->|Yes| Deadline{Deadline allows backoff?}
    Deadline -->|No| DEADLINE[DEADLINE]
    Deadline -->|Yes| Backoff[Exponential Backoff + Jitter]
    Backoff --> Next[Next Attempt]
    Max --> Final
    BUDGET --> Final
    DEADLINE --> Final
```

## 6. Circuit Breaker

方法级状态：

```mermaid
stateDiagram-v2
    CLOSED --> OPEN: consecutive failures
    OPEN --> HALF_OPEN: open duration elapsed
    HALF_OPEN --> CLOSED: probe success
    HALF_OPEN --> OPEN: probe failure
```

HALF_OPEN 同时只放行一个探测调用。

## 7. Graceful Shutdown

```mermaid
sequenceDiagram
    participant O as Operator
    participant P as Provider
    participant R as Registry
    participant C as Connected Consumers

    O->>P: stop
    P->>R: unregister
    P->>C: GO_AWAY(UNAVAILABLE)
    P->>P: stop accepting new requests
    P->>P: wait inflight
    alt inflight drained
        P->>P: close
    else drain timeout
        P->>P: cancel / close
    end
```

## 8. Compatibility Filter

每个 Provider 实例可携带：

- `peach.rpc.protocol.version`；
- `peach.rpc.schema.version`；
- `peach.rpc.schema.fingerprint`。

Consumer Snapshot 更新时：

1. 缺失 Fingerprint 的旧 Provider -> LEGACY；
2. Fingerprint 一致 -> COMPATIBLE；
3. Fingerprint 明确不一致 -> INCOMPATIBLE 并从本地目录剔除。

## 9. Observability 事件模型

一次逻辑调用：

```text
client.calls = 1
client.attempts = 1..N
client.retries = attempts - 1
client.retry.exhausted = optional final stop reason
```

这样 QPS/SLO 不会被 Retry 放大，而 Attempt 指标仍能反映网络实际负载。

## 10. 关键并发回归

自动化覆盖：

- response vs timeout；
- response vs cancel；
- cancel vs disconnect；
- drain vs new request；
- close vs heartbeat；
- HALF_OPEN concurrent probe；
- duplicate Request ID；
- random fragmentation/coalescing。

## 11. 资源所有权

| 资源 | Owner | 生命周期 |
|---|---|---|
| ServiceDirectory | Consumer Runtime | refer -> client close |
| EndpointStats | Consumer Runtime | endpoint active -> directory removal |
| Connection Group | Transport Client | endpoint use -> client close |
| Registry Subscription | Registry Adapter | subscribe -> close |
| Provider Execution Pool | Server Runtime | server start -> close |
| Observer Adapter | Spring/Core composition | runtime lifecycle |

## 12. 错误与恢复原则

- 协议错误优先 fail-fast；
- Telemetry 错误隔离；
- Registry 短时错误保留 last-known-good；
- Retry 不能替代容量；
- Circuit/Outlier 不能隐藏长期故障；
- TLS 错误不允许自动降级到明文。
