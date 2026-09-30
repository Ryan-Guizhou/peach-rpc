# Peach RPC 生产配置与安全加固

<!-- capability-status:v2-g2=engineering-ready -->

> 状态：**Engineering Current / Numeric Capacity Values Pending Evidence**  
> 本文定义生产配置原则。性能相关数值必须来自固定硬件 Evidence，不在文档中拍脑袋给出 Production 数字。

## 1. 配置原则

生产配置分为三类：

| 类型 | 来源 |
|---|---|
| 兼容与安全默认值 | 框架固定约束 |
| 容量参数 | V2-D.2-E2 / V2-D.4 Evidence |
| 业务策略 | 应用自己的延迟、吞吐、幂等与可用性目标 |

不要把开发机或 GitHub shared runner 的结果直接转换为生产参数。

## 2. Transport

必须显式评审：

- connections-per-endpoint；
- max-inflight-per-connection；
- max-frame-bytes；
- write queue 上限；
- connect timeout；
- handshake timeout；
- heartbeat interval / timeout；
- reconnect backoff。

Connection Shard 应由目标 payload、并发和 QPS/Core 证据选择。

### 危险配置

- 无依据大幅增加 max-frame-bytes；
- 无边界增加 inflight；
- heartbeat timeout 小于正常 GC/网络抖动窗口；
- reconnect backoff 过小导致恢复风暴。

## 3. Provider

默认执行模型：

- BLOCKING_VIRTUAL：阻塞业务；
- CPU：有界平台线程池；
- DIRECT：默认禁止。

生产启用 DIRECT 前必须证明：

- 方法不阻塞；
- 不执行磁盘/网络 IO；
- 不长时间占用 Event Loop；
- 有独立性能与故障回归测试。

Provider 必须结合固定 Evidence 确认：

- max-concurrent；
- CPU parallelism；
- CPU queue capacity；
- drain timeout。

## 4. Consumer Resilience

Retry 只允许显式幂等方法。

生产必须评审：

- max attempts；
- retry budget；
- retry base/max backoff；
- circuit consecutive failure threshold；
- circuit open duration；
- outlier failure threshold；
- outlier ejection duration。

Retry、Circuit 与 Outlier 是故障控制机制，不是容量不足的替代方案。

## 5. TLS / mTLS

生产优先使用 TLS 或 mTLS。

必须：

- 保持 hostname verification 开启；
- 私钥文件只授予运行用户；
- 使用独立 trust material；
- 监控证书过期；
- 验证在线证书 Reload；
- 禁止 TLS 失败后回退 PLAINTEXT。

详细配置见 [TLS / mTLS 安全指南](security.md)。

## 6. Registry

### Etcd

评审：

- endpoint 集群列表；
- namespace；
- Lease TTL；
- 凭据和 TLS；
- Watch/Lease 恢复告警。

### Nacos

评审：

- namespace；
- group；
- cluster；
- endpoint；
- username/password；
- Naming SDK 恢复行为。

Registry 不应该进入单次 RPC 热路径。

## 7. JVM

Controlled Performance Evidence 要求显式 JVM flags。

生产 JVM 参数至少需要记录：

- Heap 大小；
- GC；
- Container/CPU 限额；
- Native Memory；
- Direct Buffer 限额；
- JFR 策略。

不要把 Benchmark JVM flags 原样复制到生产，除非容量评审证明适用。

## 8. Observability

至少部署：

- logical client call QPS/latency/error；
- Retry；
- Circuit reject；
- Outlier ejection；
- Provider admission reject；
- Connection/Heartbeat；
- Registry failures/recovery；
- TLS handshake/reload；
- OpenTelemetry Trace 或等价链路诊断。

见 [生产可观测与 SLO 模板](production-observability.md)。

## 9. 推荐值的晋级规则

生产数值只能按以下链路晋级：

~~~text
Controlled Evidence
        |
Repeatability PASS
        |
Baseline Candidate
        |
Engineering Review
        |
V2-D.4 Regression Closure
        |
Environment-specific Recommended Profile
~~~

没有完成该链路时，文档只保留框架默认值与配置原则，不声明 Production Recommended QPS、线程数或超时数值。
