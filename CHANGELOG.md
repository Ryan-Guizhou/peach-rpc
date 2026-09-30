# Changelog

Peach RPC 使用语义化版本。1.0.x 保持 Wire v1、Stable Type ID、Schema Fingerprint v1 和 Public Core API 的兼容边界。

## 1.0.0

### Added

- 正式冻结 Wire v1 与 1.0.x 兼容策略。
- 完整 RC1/GA Release Workflow、Release Readiness 与 SHA256 Release Bundle。
- 面向开源使用者的需求蓝图、技术方案、功能描述、详设、快速开始、FAQ 和项目结构文档。
- GitHub 开源治理入口：Security Policy、Code of Conduct、PR/Issue 模板。

### Changed

- Maven 默认版本提升为 `1.0.0`。
- 对外文档从 V2 阶段研发日志收敛为 1.0 稳定文档体系。
- 固定硬件性能 Evidence 改为“发布官方性能/容量数字”的必需门槛，而不是开源 GA 本身的阻塞项。

### Verified

- CI。
- Release Readiness。
- Etcd Chaos。
- Nacos Chaos。
- N/N+1 Rolling Compatibility。
- Independent JVM Examples E2E。

## 1.0.0-RC1

### Added

- Stable Type ID 与冲突检测。
- Schema Fingerprint v1。
- Registry compatibility metadata 与 pre-routing incompatible provider isolation。
- N/N+1 mixed version 与 rollback 自动化。
- 协议 malformed/property/race 测试。
- Etcd leader-transfer Chaos。
- Nacos pause/unpause Chaos。
- logical-call Metrics、Retry exhausted、Circuit state、Provider admission 等生产观测信号。
- Upgrade/Rollback、Production Configuration、Dashboard、Alert 与 SLO 模板。

### Freeze

RC1 起冻结：

- Wire Protocol v1；
- Public Core API；
- Stable Type ID 规则；
- Schema Fingerprint v1；
- 已分配 Codec/Message Type；
- Registry compatibility metadata key。

## 0.1.x

预览阶段。用于建立 Core/Adapter 边界、Unary RPC、Spring Boot Starter、Registry/Transport/Codec、Resilience、Security、Observability 与性能工具链。
