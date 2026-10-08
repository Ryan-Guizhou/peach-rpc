# Peach RPC Agent 工程治理实施契约

**已由用户确认（2026-10-08），只允许分阶段提交 PR，禁止擅自合并。** 这是方案与验收契约，不代表全部任务已实现。

## Goal

在不破坏 RPC 性能/协议的条件下，统一 Codex/Cursor 的代码生成标准、命名、Java API 设计、中文标准 Javadoc、英文结构化日志与可验证质量门禁，并对存量代码做全仓审计、分批整改。

## Scope

只针对 Peach RPC。包含根 AGENTS.md、MCP 单一配置源、共享 Skills、规则/测试及异常许可、源码注释与日志、私有命名/结构、必要的 CI baseline 修复。保留主分支和 Peach Cloud 不变。

## Non-goals

不进行无关功能开发、发布或合并；不因为审美重命名公共 API、Wire/Codec 类型、注册兼容标识、配置键或序列化 Record；不伪造数据、性能回归证据或冒充修复已经验证。

## Compatibility

JDK 21、Spring Boot 3.5.4、现有 Maven reactor。1.0.x Wire v1、Public Core API、Codec/Message IDs、Stable Type IDs、Schema Fingerprint v1、Registry metadata 保持兼容。只能修改经兼容审查确认的 private/internal 名称与纯实现。

## Constraints

- Javadoc 使用中文、标准 Tag、可验证契约。类型 `@since` 必须记录**实际引入版本**；不在 Peach RPC 采用 Peach Cloud 的自定义类型头。
- 日志英文、参数化、脱敏、控制热路径日志频次；不能以新增日志降低性能。
- 数据库 MCP 默认不连接，用户在本地配置基于数据库 GRANT 实际约束的 SELECT 用户后才能启用；提示词不构成安全隔离。
- Cursor/Codex 共享行为规则，不互相拷贝不同版本 Skill；MCP 运行配置有唯一事实源。
- 只提交 Draft PR，用户确认后才可能合并。

## Plan / Migration

1. PR-0：修复当前主 CI 的 Provider readiness E2E 竞态；新回归用例。
2. PR-1：AGENTS、MCP 唯一配置源、Cursor/Codex 适配、权限/授权说明、漂移 CI 和本契约。
3. PR-2：规范全文、4 个共享 Skills、命名/禁用 API/Javadoc/日志自动化检查及正反向测试。
4. PR-3+：全仓审计产出路径/位置/风险/豁免清单；先高风险 API、SPI、Transport、Codec、Starter，再按模块修正注释/日志/私有命名。只改文档和注释的 PR 不混入有行为变化的改造。
5. 完成：Maven+Javadoc、Agent/MCP 检查、兼容测试、相关性能证据与文档链接均复核。

## Verification

`python3 scripts/check_project.py`；`mvn -B -ntp clean verify -Pquality`；Agent/MCP 的 schema 和 drift tests；java lint 规则的正负 fixture；影响 Wire/Transport/Registry 时运行 Rolling Compatibility，性能敏感代码必须 JMH/JFR 复测。每个 PR 以**该 SHA**的 CI 为准。

## Risks

旧代码的注释/命名并不全面符合新规则，首次阻断仅覆盖高置信度硬错误与新增改动；历史差异跟踪治理而非一次性机械改造。MCP 能力与权限不同，GitHub/Cursor/Codex 的 UI 审批不能代替仓库授权规则。大量重命名可破坏 Java 二进制兼容与反射映射。Shared Runner 性能 Smoke 不得写成 production p99 保证。
