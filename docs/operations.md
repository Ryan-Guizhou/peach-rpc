# OTRYX RPC 运行与排障

本页描述现有工程的基础运行检查，不能代替目标环境的容量评估或发布审批。

## 1. 先验证工程

仓库根目录运行：

```bash
python3 scripts/check_project.py
mvn -B -ntp clean verify -Pquality
bash scripts/run_example_process_e2e.sh
```

第三条命令运行独立 JVM 示例；其外部服务、端口和前置条件以 [Examples](../otryx-examples/README.md) 和脚本为准。CI 中还包含 Nacos Integration、Compatibility、Chaos、Benchmark Smoke 等独立工作流；不代表每次本地运行均执行了这些测试。

## 2. 常见失败的定位次序

| 现象 | 首先确认 | 详细资料 |
|---|---|---|
| Registry 未发现实例 | Nacos/Etcd 的服务地址、命名空间、订阅与 Provider 注册是否一致 | [Nacos](registry-nacos.md) |
| RPC 超时 | 整体 Deadline、连接状态、Provider Inflight 和线程/CPU 资源 | [配置](production-configuration.md) |
| TLS/mTLS 握手失败 | 信任链、hostname verification、证书文件、客户端证书模式 | [安全](security.md) |
| 过载和请求拒绝 | Admission、backpressure、并发、Retry Budget，拒绝原因 | [Provider Admission](provider-admission.md) |
| Trace/指标缺失 | Adapter 依赖、采集/exporter 配置和采样 | [可观测性](observability.md) |
| 滚动升级失败 | 类型、方法和 Schema Fingerprint、业务契约、Registry 隔离 | [升级回滚](upgrade-rollback.md) |

## 3. 容量与长期稳定性

固定 Runner 环境下重复进行矩阵实验、记录分位延迟、GC、allocation 和错误率。10k 逻辑并发是 soak harness 的**测试负载设置**，不等于已证明生产可长期稳定支撑 10k 并发。

原始证据与脚本详见 [性能证据](performance-evidence.md)、[容量规划](capacity-planning.md) 和 [Soak 验收](soak-acceptance.md)。

## 4. 兼容、回滚与风险边界

- Peach RPC 1.x 到 OTRYX 2.0 的公开 API 命名空间变更属于 Breaking Change；**不要直接将两代服务混部视为受支持**。
- 保留旧版稳定分支，先使用分区部署或蓝绿过渡，并以真实业务契约验证互通边界。
- `2.0.0-SNAPSHOT` 非 GA；商标、参考素材和 Maven Central 发布权限仍有独立验收前置条件。

参见 [迁移指南](migration-to-otryx.md)、[Wire 兼容性](wire-compatibility.md) 与 [公开发布核查](publication-readiness.md)。
