# Peach RPC 升级与回滚指南

> 状态：**Engineering Current**  
> 适用于 Wire Protocol v1 内的 N/N+1 滚动升级。若业务契约不兼容，必须使用新的 ServiceKey.version。

## 1. 发布前检查

发布前必须确认：

- Repository checks 通过；
- Maven quality Reactor 通过；
- 协议 Robustness Suite 通过；
- Etcd/Nacos Adapter 集成测试通过；
- 本版本没有未声明的 Wire Protocol 修改；
- 新旧版本 Compatibility Matrix 已更新；
- Provider/Consumer 使用同一业务契约时 Schema Fingerprint 一致；
- 若涉及性能内核修改，V2-D.4 回归门禁通过；
- 若涉及 Registry/Transport 恢复逻辑，相关 Chaos 门禁通过。

## 2. 推荐滚动顺序

同一 Wire v1 且业务 Schema 未变化时，推荐先升级 Provider，再升级 Consumer：

~~~text
N Provider + N Consumer
        |
        v
N/N+1 Provider mixed
        |
        v
N+1 Provider + N Consumer
        |
        v
N+1 Provider + N/N+1 Consumer mixed
        |
        v
N+1 Provider + N+1 Consumer
~~~

Provider-first 不是协议硬要求，而是推荐的操作顺序，便于先观察新 Provider 的注册、TLS、容量和服务端错误指标。

## 3. Provider 升级

每批 Provider：

1. 从发布系统摘除或进入 Graceful Drain；
2. Provider 先从 Registry 注销；
3. 向现有连接发送 GO_AWAY；
4. 等待 inflight 完成或 drain timeout；
5. 停止 N 实例；
6. 启动 N+1；
7. 确认 Registry 新实例可见；
8. 确认 Schema Fingerprint 与预期契约一致；
9. 确认 TLS/Heartbeat/Registry 指标正常；
10. 再进入下一批。

禁止在所有 Provider 同时下线后再整体启动。

## 4. Consumer 升级

Consumer 升级期间：

- N Consumer 会忽略 N+1 Provider 的新增兼容 Metadata；
- N+1 Consumer 会保留没有 Fingerprint 的 N Provider 为 LEGACY；
- N+1 Consumer 会过滤明确 Fingerprint 不一致的 Provider；
- Retry 不应被当作升级兜底，只有标记为幂等的方法才允许自动 Retry。

升级后重点观察：

- logical client call error rate；
- retry rate；
- circuit reject；
- outlier ejection；
- reconnect；
- heartbeat timeout；
- provider admission reject。

## 5. Schema 变更

以下变更会改变当前严格 Schema Fingerprint：

- 增删 RPC 方法；
- 修改参数或返回类型；
- 修改 DTO 字段；
- Record Component 改变。

不兼容变更必须使用新的业务版本，例如：

~~~text
demo.UserService:1.0.0:default
demo.UserService:2.0.0:default
~~~

迁移期间可以让两个 ServiceKey Version 同时存在。

## 6. 回滚

### Provider 回滚

1. 停止继续扩大 N+1；
2. Drain N+1 Provider；
3. 启动 N Provider；
4. 等待 Registry Snapshot 恢复；
5. N+1 Consumer 会把缺失 Fingerprint 的 N Provider识别为 LEGACY；
6. 确认 Client logical call、Retry、Circuit、Registry 指标恢复正常；
7. 再逐批回滚剩余 Provider。

### Consumer 回滚

只要 Wire Protocol 仍为 v1，N Consumer 可继续调用 N+1 Provider，因为新增 Schema Metadata 位于 Registry 控制面且旧 Consumer 会忽略。

### 必须停止回滚的情况

出现以下情况应停止自动回滚并人工评审：

- 数据模型已经发生不可逆业务迁移；
- ServiceKey.version 被错误复用；
- Wire Protocol Version 已变化；
- Codec ID 或 Payload 格式被不兼容修改；
- 新版本依赖旧版本不存在的业务语义。

## 7. 发布后验证

至少确认：

- Registry 实例数量符合预期；
- active connections 恢复；
- heartbeat timeout 没有持续增长；
- TLS handshake failure 没有持续增长；
- client logical call error rate 回到基线；
- Provider admission reject 没有持续增长；
- 没有持续 circuit open / outlier ejection 风暴。

## 8. 与 1.0.0-RC1 的关系

进入 1.0.0-RC1 后：

- Wire Protocol Freeze；
- Public Core API Freeze；
- Stable Type ID 规则 Freeze；
- Schema Fingerprint 规范版本 Freeze。

RC1 之后若必须改变上述契约，需要明确兼容策略或升级 Protocol/Schema 版本。
