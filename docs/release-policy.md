# Peach RPC 发布策略与 Production Operations

> 状态：**Engineering Current / 1.0 RC Not Yet Declared**  
> 本文定义 V2-G.2 发布工程。当前项目仍是 Preview，不因发布工具齐全自动升级为 Production Ready。

## 1. 版本阶段

~~~text
0.1.x Preview
    |
V2-D/E/F/G Engineering Gates
    |
1.0.0-RC1
    |
RC validation
    |
1.0.0 GA
~~~

V2-G.2 完成不等于 1.0.0-RC1。RC1 仍要求所有外部 Evidence/Chaos/Compatibility 门禁满足。

## 2. 发布前 Gate

发布候选至少需要：

### Repository

- scripts/check_project.py PASS；
- 中英文 README 同步；
- 本地 Markdown Link PASS；
- Core 第三方依赖边界 PASS。

### Build

- JDK 21；
- Maven 3.9+；
- mvn clean verify -Pquality PASS；
- Javadoc Warning = 0；
- Source JAR 可生成；
- Reactor 模块与 Parent 版本一致。

### Compatibility

- Stable Type ID 规则存在；
- Schema Fingerprint 规范存在；
- N/N+1 Compatibility Matrix 存在；
- Upgrade/Rollback Runbook 存在；
- 不兼容业务 Schema 使用新的 ServiceKey.version。

### Performance

进入 RC 前必须：

- E1 Handoff PASS；
- E2 Repeatability PASS；
- Baseline Candidate 完成 Engineering Review；
- 若 V2-D.3 有实际优化，D4 Closure PASS；
- Capacity Planning 使用真实固定硬件数字。

### Robustness

- Protocol malformed/truncation/property tests PASS；
- Transport cancel/drain/heartbeat/reconnect PASS；
- Etcd Chaos PASS；
- Nacos Chaos PASS；
- 长时间 fixed-environment recovery/soak 的外部门禁满足。

### Operations

- Production Configuration Guide；
- Dashboard；
- Alert template；
- SLO template；
- Security Guide；
- Upgrade/Rollback Guide。

## 3. Artifact

正式 Release 需要验证：

- peach-rpc-core；
- Codec/Transport/Registry/Proxy/Observability Adapters；
- Spring Boot autoconfigure/starter；
- source JAR；
- Javadoc；
- POM/flattened metadata；
- Examples 不作为应用依赖发布入口；
- Benchmarks 不作为业务 Starter 传递依赖。

## 4. Version Policy

### Preview

0.1.x 允许在明确 Release Notes 下调整未冻结 API/Wire 行为。

### RC1

进入 1.0.0-RC1 后冻结：

- Wire Protocol v1；
- Public Core API；
- Stable Type ID range/algorithm；
- Schema Fingerprint v1；
- 保留 Metadata key；
- Codec/Message Type 已分配编号。

### GA

1.0.x 默认只接受：

- Bug fix；
- Security fix；
- 不破坏兼容的 Observability/Operational improvement。

破坏兼容的 Wire 或 API 修改进入新的兼容版本规划。

## 5. Deprecation Policy

公开 API 弃用必须：

1. 先标记 Deprecated；
2. Release Notes 解释替代入口；
3. 至少跨一个 Minor Release 保留；
4. 删除前确认下一个兼容边界。

Wire 编号和 Registry Metadata key 不通过普通 Java Deprecated 流程复用。

## 6. Release Notes

每个 Release Notes 至少列出：

- Added；
- Changed；
- Fixed；
- Compatibility；
- Security；
- Performance；
- Operational notes；
- Upgrade；
- Rollback；
- Known limitations。

如果没有固定硬件 Evidence，不写“性能提升 X%”之类数字。

## 7. Upgrade / Rollback

操作步骤见 [升级与回滚指南](upgrade-rollback.md)。

生产配置见 [生产配置与安全加固](production-configuration.md)。

Wire 规则见 [Wire Compatibility](wire-compatibility.md)。

## 8. Secret 与日志

发布前必须确认：

- Registry Credential 的 toString 脱敏；
- 私钥不进入 Artifact；
- Benchmark TLS key 不进入 Evidence；
- Runtime log 使用英文；
- Error message 不暴露密码/Token/私钥；
- Dashboard/Alert 不含 Secret。

## 9. 自动 Release Readiness

仓库提供 scripts/check_release_readiness.py，用于检查 G2 所需静态资产与状态。

该脚本不替代真实性能/Chaos 结果，它只证明 Release 工程资产完整。

独立 Release Readiness Workflow 会执行：

~~~text
Repository checks
    |
Release static checks
    |
Maven quality build
    |
Package artifacts
    |
Artifact inventory
~~~

## 10. 1.0.0-RC1 前最终人工确认

进入 RC1 前必须人工确认：

- E1/E2/D4 Evidence 是否来自受控固定硬件；
- D3 是否严格按 Evidence Scope 实施；
- Chaos 是否在目标支持环境真实执行；
- Compatibility Matrix 是否覆盖本次升级组合；
- SLO/Alert threshold 是否由目标环境负责人确认；
- Rollback 是否在预发布环境演练。

通过后才允许把 project 状态从 Preview 改为 Release Candidate。
