# Peach RPC 生产配置与安全加固

> 状态：**1.0.1 Release Prep / Numeric Capacity Values Require Environment Evidence**

## 1. 配置原则

生产配置分为：

| 类型 | 来源 |
|---|---|
| 协议/安全默认值 | 框架固定 |
| 容量参数 | 目标环境 Evidence |
| 业务策略 | 应用自己的延迟、吞吐、幂等和可用性目标 |

## 2. Transport

必须评审：

- connections-per-endpoint；
- max-inflight-per-connection；
- max-frame-bytes；
- write queue；
- connect/handshake timeout；
- heartbeat interval/timeout；
- reconnect backoff。

危险做法：

- 无依据扩大 frame/inflight；
- heartbeat timeout 小于正常 GC/网络抖动窗口；
- reconnect backoff 过小形成恢复风暴。

## 3. Provider

默认：

- BLOCKING_VIRTUAL；
- CPU 有界线程池；
- DIRECT 默认禁止。

DIRECT 只能用于经过证明的极短非阻塞逻辑。

容量参数：

- max-concurrent；
- 分层 Admission 的 max-inflight-bytes / 每服务与方法限制（见 [Provider Admission 指南](provider-admission.md)，PR-D Draft）；
- CPU parallelism；
- CPU queue capacity；
- drain timeout。

## 4. Consumer Resilience

生产评审：

- timeout；
- max attempts；
- Retry Budget；
- retry backoff；
- Circuit threshold/open duration；
- Outlier threshold/ejection duration。

Retry/Circuit/Outlier 是故障控制，不是容量替代。

## 5. TLS/mTLS

生产优先 TLS/mTLS：

- hostname verification 保持开启；
- 私钥只授予运行用户；
- Trust material 独立管理；
- 监控证书过期；
- 验证在线 Reload；
- TLS 失败禁止回退 PLAINTEXT。

详见 [安全指南](security.md)。

## 6. Registry

### Etcd

评审 endpoint 集群、namespace、Lease TTL、凭据/TLS、Watch/Lease 恢复告警。

### Nacos

评审 endpoint、namespace、group、cluster、username/password 和 SDK 恢复行为。

Registry 不进入单次 RPC 热路径。

## 7. JVM

至少记录：

- Heap；
- GC；
- CPU/Container limit；
- Native Memory；
- Direct Buffer；
- JFR 策略。

Benchmark JVM 参数不能未经验证直接复制到生产。

## 8. Observability

至少部署：

- logical call QPS/latency/error；
- retry / retry exhausted；
- circuit state/reject；
- outlier ejection；
- Provider admission；
- connection/heartbeat；
- Registry failure/recovery；
- TLS handshake/reload；
- Trace 或等价链路诊断。

见 [生产可观测与 SLO](production-observability.md)。

## 9. 容量数字

任何 QPS、线程数、连接数、Heap、p99/p99.9 推荐值都必须来自目标环境 Evidence。见 [容量规划](capacity-planning.md)。
