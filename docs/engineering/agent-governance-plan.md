# Peach RPC Agent 工程治理实施契约

**实施契约：** 2026-10-08 用户确认原始治理范围；2026-10-09 已另行批准合并 PR #27–#39，并要求建立冻结稳定分支，后续**本次已授权工程治理**阶段经相关 CI 检查通过后合并 `main`。仅是范围内的代码交付授权，不包括 Release、删除分支、仓库权限变更或数据库写操作。详见 [稳定基线与 Mainline 交付](stable-baseline.md)。

## Goal

在不破坏 RPC 性能/协议的条件下，统一 Codex/Cursor 的代码生成标准、命名、Java API 设计、中文标准 Javadoc、英文结构化日志与可验证质量门禁，并对存量代码做全仓审计、分批整改。

## Scope

只针对 Peach RPC。包含根 AGENTS.md、MCP 单一配置源、共享 Skills、规则/测试及异常许可、源码注释与日志、私有命名/结构、必要的 CI baseline 修复。保留主分支和 Peach Cloud 不变。

## Non-goals

不进行无关功能开发、发布、未经授权的其他合并；不因为审美重命名公共 API、Wire/Codec 类型、注册兼容标识、配置键或序列化 Record；不伪造数据、性能回归证据或冒充修复已经验证。

## Compatibility

JDK 21、Spring Boot 3.5.4、现有 Maven reactor。1.0.x Wire v1、Public Core API、Codec/Message IDs、Stable Type IDs、Schema Fingerprint v1、Registry metadata 保持兼容。只能修改经兼容审查确认的 private/internal 名称与纯实现。

## Constraints

- Javadoc 使用中文、标准 Tag、可验证契约。类型 `@since` 必须记录**实际引入版本**；不在 Peach RPC 采用 Peach Cloud 的自定义类型头。
- 日志英文、参数化、脱敏、控制热路径日志频次；不能以新增日志降低性能。
- 数据库 MCP 默认不连接，用户在本地配置基于数据库 GRANT 实际约束的 SELECT 用户后才能启用；提示词不构成安全隔离。
- Cursor/Codex 共享行为规则，不互相拷贝不同版本 Skill；MCP 运行配置有唯一事实源。
- 当前项目治理阶段采用非堆叠的 Mainline PR：`base=main`，当前 Head 所需 CI 通过后在已有授权范围内合并；合并后仍需验证新 main CI。

## Plan / Migration

1. **已合并：** PR #27–#39 覆盖 Provider readiness、AGENTS/MCP、四个 Skills、禁用 API、ArchUnit、Checkstyle 建议基线及日志脱敏。
2. **已合并：** PR #40 将全 Maven Reactor 的 Checkstyle 零违规报告升级为 CI 阻断门禁（合并后的 `main` CI 单独核验）。
3. **已冻结：** `stable/agent-quality-2026-10-09` 指向通过 CI 的 #27–#39 基线 SHA `8f051b78d74978c5417394ed2cd22467e077c15f`，不跟随 main 更新。
4. **仍待治理：** Consumer/Transport 异常隔离和故障控制帧测试；公共 API Javadoc 语义；私有命名/职责；Windows MCP 实测与 npm 版本供应链核验；受控性能数据。
5. **验收标准：** 每一新 PR 以准确 Head SHA 的 CI、Javadoc、Agent/MCP、Checkstyle 和必要的兼容/性能证据为依据；通过后合并 main，再核验 main push CI，异常停止推进。

## Verification

`python3 scripts/check_project.py`；`mvn -B -ntp clean verify -Pquality`；Agent/MCP 的 schema 和 drift tests；java lint 规则的正负 fixture；影响 Wire/Transport/Registry 时运行 Rolling Compatibility，性能敏感代码必须 JMH/JFR 复测。每个 PR 以**该 SHA**的 CI 为准。

## Risks

旧代码的注释/命名并不全面符合新规则，首次阻断仅覆盖高置信度硬错误与新增改动；历史差异跟踪治理而非一次性机械改造。MCP 能力与权限不同，GitHub/Cursor/Codex 的 UI 审批不能代替仓库授权规则。大量重命名可破坏 Java 二进制兼容与反射映射。Shared Runner 性能 Smoke 不得写成 production p99 保证。
