# Peach RPC Agent 工程治理：可续写断点台账

> 检查点时间：2026-10-09（Asia/Singapore）。**本文件是已完成步骤与遗留任务快照，不能替代最新 GitHub PR/CI。** 用户要求：超过 10 分钟无进展立即中断当前等待，保留现场，下一轮从断点继续；任何 PR 未经授权禁止合并。

## 已获得用户明确授权的范围

仅 Peach RPC，全仓审计+按 PR 安全整改；中文标准 Javadoc 与英文参数化日志、命名/禁用 API、大项目架构规范和 Cursor/Codex 的同源配置。MySQL 默认不接入，只有经用户批准的独立只读账户（以实际 GRANT 为准）才能用。不可改动 1.0.x Wire v1、Codec/Type ID、Schema v1、Public Core API 和 Starter 兼容设置。

## PR 堆叠关系与当前状态（全部 Draft）

| 阶段 | PR | Head branch | 本轮完成情况 |
|---|---|---|---|
| 0：Provider readiness E2E 基线 | [#27](https://github.com/Ryan-Guizhou/peach-rpc/pull/27) | `chore/agent-quality-00-e2e-baseline` | 最新 CI / Rolling / Release SUCCESS；已修复 wildcard bind 误判 |
| 1：AGENTS/MCP 工作边界 | [#28](https://github.com/Ryan-Guizhou/peach-rpc/pull/28) | `chore/agent-quality-01-governance` | Agent Governance、CI、Release SUCCESS |
| 2：编码规范和 4 Skills | [#29](https://github.com/Ryan-Guizhou/peach-rpc/pull/29) | `chore/agent-quality-02-standards-skills` | Java Agent Quality、Governance、Release SUCCESS；单次主 CI 在 Transport 测试偶发失败，见 #31 |
| 3：首批存量注释/日志 | [#30](https://github.com/Ryan-Guizhou/peach-rpc/pull/30) | `chore/agent-quality-03-java-remediation` | Java/Agent、Etcd Chaos、Rolling、Release SUCCESS；单次主 CI 在重连生命周期断言失败，见 #31 |
| 4：Transport Drain/重连修复 | [#31](https://github.com/Ryan-Guizhou/peach-rpc/pull/31) | `fix/agent-quality-04-transport-drain-lifecycle` | **最新 CI、Rolling、Release、Java Agent Quality 全 SUCCESS**（Head `b27b531c`） |
| 5：Core/资源/日志规则增强 | [#32](https://github.com/Ryan-Guizhou/peach-rpc/pull/32) | `chore/agent-quality-05-dependency-and-security-gates` | 已创建 B001/R002/R003/L003/L004/N001 规则及正反向测试，CI 以**最终 Head SHA**为准待复核 |

**重要**：这是堆叠 PR，各阶段从上一开发分支派生。不要为了让中间 PR 的瞬时 CI 绿而强行改写历史、自动合并或把不同阶段的纯文档和行为改动混在一个提交中。PR #31 的累计版本已解决上述测试失败，但 PR #29/#30 原 SHA 的单次记录仍真实保留。

## 可复用 Skills

`.agents/skills/using-peach-rpc-java-engineering`、`using-peach-rpc-compatibility`、`using-peach-rpc-performance`、`review-peach-rpc-changes`；都有独立 `SKILL.md` + `agents/openai.yaml`。Cursor/Codex 统一发现 `.agents/skills`，不要复制到各自文件夹导致漂移。

## 全仓质量扫描结果与边界

PR #29 的 212 个 Java 文件审计（commit `230498fb`）：**0 条 error 和 11 条 warning**（均与 `catch(Throwable)` 的异常隔离审查有关）。**这只覆盖当时的 9 条规则**，不等于全仓代码/日志/Javadoc 全合规。PR #32 已扩展规则集，必须重新生成审计报告。

PR #30 主要为有明确源码证据的约 10 个 Runtime/Transport/Codec/Registry/Starter 文件补充中文契约和优化结构化日志。未改协议、公共签名与任意 `catch(Throwable)` 行为。详情见 `docs/engineering/java-existing-code-audit.md`。

## 待开发和待验证（下一轮优先级）

1. **核实 PR #32 Head 最新 CI**：`Java Agent Quality`、`Agent Governance`、Maven/Javadoc、Release；若失败立即按 Job 日志修复，不将任何旧提交绿色视为当前版本通过。
2. 继续 Java 21 全仓注释/日志/命名审计，分模块形成 **带具体路径/行号** 的清单，区分 Public ABI 不可直接改名、private 可整理、低风险注释、可能涉及行为的重构。
3. 11 处 `catch(Throwable)` 逐个补充有证据的取消/关闭/错误测试再审查。不能机械改成 `catch(Exception)` 造成 Future 未完成、资源泄露。
4. 针对 SPI/Codegen/Registry/Starter 的依赖和命名，补 AST/Checkstyle/ArchUnit 等可靠规则，先运行 Advisory 模式查历史误报，再逐步纳入 PR 阻断。
5. Windows Cursor/Codex 的 MCP **实际启动/权限测试仍没有用户机器环境凭据**；`config/agent-mcp.json` 沿用历史 npm package 名，需锁版本并审核供应链后才能称生产就绪。
6. GitHub branch protection 仍未修改；如需将 Quality Gate 变为强制，必须另行获得修改仓库权限的明确授权。
7. 全量整改完毕后执行 CI/Javadoc/Rolling Compatibility；涉及并发/性能的代码做相应 JMH/JFR 和受控证据。无受控证据不能声称 p99 无回归或吞吐提升。

## 断点续写命令/证据

从 GitHub 检查 `main`、PR #27-#32 的 Head/Base/Draft 状态和对应 SHA 的 Actions，再执行：

```bash
python3 scripts/check_project.py
python3 scripts/sync_agent_mcp.py --check
python3 scripts/test_agent_mcp.py
python3 scripts/test_java_conventions.py
python3 scripts/validate_repo_skills.py
python3 scripts/check_java_conventions.py --audit --report target/java-conventions-audit.json
mvn -B -ntp clean verify -Pquality
```

CI 正在运行时不要持续空循环。一个检查超过十分钟无进展，记录 Run ID、Job/Step、Head SHA、最后日志及是否可安全取消，然后停止当前等待并报告该断点。优先转向无依赖、可独立提交的审查，不重复开相同 PR。

## 停止条件

不能确定实现/版本/兼容性、CI 阻塞、外部环境无权限、工具网络不可达或者待处理变更超出上述授权范围时，不做猜测性大规模代码修改。将问题、影响、下一步可验证操作保存在 PR 或本台账。**不合并任何 PR。**
