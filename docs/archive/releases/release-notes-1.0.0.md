# Peach RPC 1.0.0 Release Notes

> 类型：General Availability  
> 兼容基线：Wire v1 / Schema Fingerprint v1 / JDK 21。

## 1. GA 定位

Peach RPC 1.0.0 是第一个稳定开源版本。它提供 Java Unary RPC、注册发现、高可用治理、安全、可观测、兼容升级、测试和发布工程的完整闭环。

## 2. 相对 RC1

GA 不引入新的不兼容协议能力，重点完成：

- 对外文档体系收口；
- Maven 版本与发布元数据收口；
- RC1/GA 自动 Release Workflow；
- CHANGELOG / Security / Community Governance；
- Quick Start / FAQ / Release Readiness；
- 删除阶段性 V2 计划文档和失真状态；
- 将性能 Evidence 的责任边界改为“官方性能/容量声明必须有证据”。

## 3. GA 稳定性修复

- Nacos subscription 使用 NamingEvent 主通道 + 5 秒完整视图 reconcile 兜底，修复远端最后一个 Provider 注销时 empty snapshot 偶发不收敛的问题。

## 4. 生产行为

### Consumer

- 本地服务目录；
- P2C + EWMA；
- Retry 仅限显式幂等方法；
- Circuit / Outlier；
- Deadline 覆盖连接、握手与请求；
- heartbeat / reconnect。

### Provider

- Registry 注册/注销；
- BLOCKING_VIRTUAL / CPU / guarded DIRECT；
- admission；
- CANCEL；
- GO_AWAY / graceful drain。

### Security

- TLS/mTLS；
- Hostname Verification；
- PEM reload；
- Credential/Secret 脱敏。

### Observability

- Micrometer；
- OpenTelemetry；
- JFR；
- Grafana Dashboard；
- Prometheus Alert Example。

## 5. Compatibility

自动化验证：

```text
N Consumer   -> N Provider
N+1 Consumer -> N Provider
N Consumer   -> N+1 Provider
N+1 Consumer -> N+1 Provider
N+1 Consumer -> N Provider rollback
```

## 6. 获取与验证

源码：

```bash
git clone https://github.com/Ryan-Guizhou/peach-rpc.git
cd peach-rpc
mvn -B -ntp clean verify -Pquality
```

正式 Release Workflow 会生成：

- `peach-rpc-1.0.0-artifacts.tar.gz`；
- `SHA256SUMS`；
- Artifact Inventory。

## 7. 限制

见 [FAQ](../../faq.md) 和 [ROADMAP](../../../ROADMAP.md)。
