# PR-D：Provider 分层 Admission 与 inflight-byte budget

> **状态：Draft PR 实现说明；尚未合并 main。** 此方案作用于 Provider 的**已准入请求 Frame**，并非 JVM 整体堆内存硬隔离。产线吞吐、p99、p99.9 和 10k 负载需要独立受控 Evidence。

## 1. 背景和边界

此前 Provider 仅通过全局 Semaphore 控制 `maxConcurrent`，多个服务共用全部配额，且无法约束在途请求 Frame 的累计字节数。PR-D 增加**全局 → 服务 → 方法**三级预算，并统一异常/取消/完成时的令牌回收。

~~~mermaid
flowchart LR
    T[Vert.x TCP / Frame validation] --> S[服务/方法与 Codec 路由验证]
    S --> A{Admission}
    A -->|global slots + bytes| G[Global Budget]
    G -->|per-service slots + bytes| B[Service Budget]
    B -->|per-method slots + bytes| M[Method Budget]
    M -->|accepted| W[CPU / Virtual Worker]
    W -->|success / error / cancel| L[Idempotent Lease release]
    A -->|denied| R[OVERLOADED + reason]
    G -->|denied| R
    B -->|denied| R
    M -->|denied| R
~~~

该方案**不会阻塞 EventLoop 等待额度**：`Semaphore.tryAcquire` 与有限 CAS，失败立即返回 `OVERLOADED`。并发/字节预算在服务注册结束、Provider 启动之前静态计算，不引入动态数据库查询。

## 2. 配额与服务隔离

- 全局请求并发由已有 `max-concurrent` 控制。
- 全局 Frame 字节数由新增 `max-inflight-bytes` 控制。
- 启动时根据已注册服务数，把这两项额度以服务 ID 的确定性顺序等额分配（余数按稳定顺序发放）。
- 每个服务独立享有自己的固定并发额度与 Frame 字节额度。**各服务配额之和不超过全局上限**，因此一个繁忙服务无法借走另一个服务的保留额度。
- `max-concurrent-per-service` 与 `max-inflight-bytes-per-service` 可进一步缩小单服务上限，不允许放大已分配的配额。
- 方法层始终存在；未显式设限时共享所属服务上限。需要避免同一服务内热点方法挤占其他方法时，设置 `max-concurrent-per-method` 和 `max-inflight-bytes-per-method`。
- 若注册服务数超过全局并发配额，或者超过全局 Frame 字节单位，启动失败，不静默禁用某个服务。

**代价：** 静态服务配额可以防止服务互相挤占，但低流量服务保留的配额不会被繁忙服务借用，可能降低单服务的峰值吞吐。按权重进行弹性借用需额外的公平调度与隔离证明，本 PR 没有实现。方法级限额越严格，也越可能降低最大吞吐。

## 3. Spring Boot 配置

~~~yaml
peach:
  rpc:
    server:
      max-concurrent: 4096
      admission:
        max-inflight-bytes: 268435456
        max-concurrent-per-service: 0
        max-concurrent-per-method: 0
        max-inflight-bytes-per-service: 0
        max-inflight-bytes-per-method: 0
~~~

`0` 表示自动沿用对应分配上限；字节总预算必须是正整数。

示例：注册 2 个服务、`max-concurrent=4096` 时，每个服务得到 2048 个独立并发许可，且各拥有最多 128 MiB 已准入 Frame 预算。**这是配置额度，不是基于生产 Evidence 的吞吐推荐值。**

程序化调用：

~~~java
PeachRpcServer.builder()
    .maxConcurrent(4096)
    .admissionOptions(new RpcProviderAdmissionOptions(
        256L * 1024 * 1024, // Global inflight Frame bytes
        0,                  // Auto per-service concurrent slots
        256,                // Per-method concurrent slots
        0,                  // Auto per-service bytes
        16L * 1024 * 1024   // Per-method Frame bytes
    ))
    // 继续设置 serviceRegistrar、transportServer、codecRegistry、bindEndpoint
    .build();
~~~

上述片段仅展示新参数，需要同时完成其他必需 Builder 依赖。

## 4. 归还、竞态及可观测性

### 单次释放

`ProviderAdmissionController.Lease` 使用原子状态保证最多释放一次全部六项预算（全局/服务/方法的并发和字节）。响应 Future 无论成功、业务错误、CPU 队列拒绝还是取消，终态都会归还 Lease。

既有分散 `admission.release()` 调用已经迁移为统一 Future 终态回收。异常路径统一完成同一个 Result Future，避免重复释放、Observer 计数不平衡及未结束的 Future。

### 拒绝原因

`RpcObserver.onServerAdmissionRejected(serviceId, methodId, reason)` 发出低基数原因：

- `frame-exceeds-budget`
- `global-concurrency` / `service-concurrency` / `method-concurrency`
- `global-inflight-bytes` / `service-inflight-bytes` / `method-inflight-bytes`
- 原有执行器 `cpu-queue` / `async-completion-queue`

`RpcObserver.onServerInflightChanged(int)` 记录逻辑在途请求量，新增 `onServerInflightBytesChanged(long)` 记录已接纳 Frame 字节增减。复合 Observer 会把事件安全分发给所有启用的采集适配器；具体 Micrometer/JFR 指标映射需要使用方适配，**不能把事件 API 当作现成仪表盘指标**。

### 必须披露的取消语义

当业务 Future 被取消，逻辑请求的准入额度会随结果终态释放。底层业务若忽略中断，仍可能短时间持有原始 Frame 或额外对象；因此不能声称取消瞬间完成了物理内存释放。

## 5. 内存保护边界

本 PR 只预算 **Core 已接纳请求的完整 raw Frame 大小**。以下资源不在此统计：

- 在 Transport 编帧完成之前积累的 ByteBuf/Buffer；
- Transport 已排队但尚未进入 Core 的请求；
- Fory 反序列化期间及返回的对象图；
- 待发送的响应 Buffer；
- 用户业务代码中的堆对象、Native Memory 和线程堆栈。

**因此“在途请求 Frame 有界”不等于“进程整体内存绝对有界”。** 同时结合已有 `max-frame-bytes`、`max-write-queue-bytes`、Fory 的 payload/对象图保护以及 Consumer `max-inflight-per-connection`，并用实测确定 JVM Heap、GC、Native Memory 余量。PR-E 将进一步处理热路径 Buffer 和分配热点。

## 6. 验收

CI 基础质量门：

~~~bash
mvn -B -ntp verify
~~~

针对性测试：

~~~bash
mvn -B -ntp -pl peach-rpc-core -am \
  -Dtest=ProviderAdmissionControllerTest,PeachRpcServerAsyncExecutionTest,RpcObserverTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
~~~

必须验证：

- 并发/字节配额永不超限，拒绝路径没有部分额度泄漏；
- 同时请求不同服务时，一方过载不阻止另一方使用保留额度；
- 完成、错误、队列拒绝、取消、关闭等竞态不会造成负数/重复释放；
- 错误状态与低基数拒绝 reason 的可观测性正确；
- 0/非法配置 fail-fast；滚动兼容不变更 Wire v1；
- 独立受控 p99 / 吞吐 / allocation 与过载 10k soak（**当前待验收**）。

## 7. 影响和迁移

本 PR **不修改** Wire v1、Registry Metadata、Service Fingerprint 或 RPC DTO。Provider 多服务共享配额改为静态分区，可能改变多服务部署的最大可用并发比例；上线前必须统计真实各服务需求，评估权重与峰值并发，不能无条件复制默认值。

相关：[容量规划](capacity-planning.md)、[生产配置](production-configuration.md)、[性能证据](performance-evidence.md)、[Dubbo 对比基准](dubbo-comparison.md)。
