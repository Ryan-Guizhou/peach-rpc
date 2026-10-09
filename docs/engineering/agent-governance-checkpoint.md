# Peach RPC Agent 工程治理：断点续写台账

> 更新时间：2026-10-09（Asia/Singapore）。这是代码分支提交时的证据快照；恢复任务时必须查询**最新 GitHub Head SHA 和对应 CI**，不能把过期的绿灯当作现状。用户要求：单项外部等待持续无进展超过十分钟时，保存 Run/Job/日志与下一命令并停止该等待。

## 1. 最新授权与主分支

用户已经在 2026-10-09 明确授权**合并当时已有的 PR**，因此 #27–#39 已依次使用保留历史的 merge commit 合并到 main。该授权不自动适用于之后新开的 PR 或 Release、删除分支、数据库操作、变更 GitHub 权限。

- 仓库：[Ryan-Guizhou/peach-rpc](https://github.com/Ryan-Guizhou/peach-rpc)
- 此轮合并后 main：`8f051b78d74978c5417394ed2cd22467e077c15f`，最后合并 #39。
- #27–#39 的 GitHub PR API 全部为 `closed + merged`，当时开放 PR 为零。
- 合并后 [Agent Governance](https://github.com/Ryan-Guizhou/peach-rpc/actions/runs/37880262218) SUCCESS；[main CI](https://github.com/Ryan-Guizhou/peach-rpc/actions/runs/37880262195) 在此断点初次核查时尚未完成，**恢复时先查看真实结果**。
- #29/#30 的旧 SHA 有 Transport 竞态/重连测试失败，#31 后续修复并通过完整 CI；不能倒填旧 SHA 的测试记录。

## 2. 已合并研发阶段

| 阶段 | 已合并 PR | 能力 |
|---|---|---|
| 0–3 | [#27–#30](https://github.com/Ryan-Guizhou/peach-rpc/pulls?q=is%3Apr+is%3Amerged) | Provider readiness 基线；Agent/MCP；四个 Skills；编码规范与首批 Javadoc、日志整改 |
| 4–6 | #31、#32、#33 | Drain/重连；Core 分层、无界资源和日志 API 检查；Nacos 可中断周期任务与异常 Future 生命周期测试 |
| 7–8 | #34、#35 | ArchUnit 字节码级依赖方向检查；Checkstyle 全仓建议性 AST 扫描 |
| 9–12 | #36、#37、#38、#39 | Provider 与 OpenTelemetry 异常脱敏、公共 API Javadoc 审计、JMH 基准方法契约补充 |

最新已合并 #39 的 [Java Style Audit](https://github.com/Ryan-Guizhou/peach-rpc/actions/runs/37875650253) 明确报告 **19 份 XML、208 个 Java 文件、0 条 Checkstyle 违规**。这只说明当时配置的规则没有发现问题，不说明 Java 线程安全、日志敏感数据流或公共契约完全合规。

## 3. 当前继续阶段：PR-13（尚未获授权合并）

开发分支 `chore/agent-quality-13-enforce-checkstyle`，基于上述 main SHA。目标是将已经达到零违规的 Checkstyle 建议性扫描升级为真实的 CI Quality Gate。

本阶段代码：
- 根据根 POM 和嵌套 `modules` 动态验证**主 Maven Reactor 每个模块**都有对应 `checkstyle-result.xml`，拒绝报告缺失、额外报告及目录穿越；
- 汇总完整 XML 后，`--enforce-zero` 对所有 Checkstyle finding（包括 warning）返回失败，报告与原始 XML 继续上传；失败不能写成“检查通过”；
- PR 和 main 的 Java Style Audit 触发器覆盖 Checkstyle 配置、汇总器及生产源码；
- 新增零违规、warning、空报告、缺失模块、意外模块与非法 Maven 模块路径测试；
- `AGENTS.md`、[Java 编码规范](java-coding-standard.md)、[Style Audit](java-style-audit.md) 文档同步。

不更改 Java Public Core API、Wire v1、Codec/Type ID、Schema v1、Spring 配置键或运行时逻辑。禁止直接修改 main：新阶段只提交 Draft PR，由用户决定是否合并。

## 4. 尚需持续治理的真实缺口

1. `catch(Throwable)` 已逐项审计并保留必要 Future 隔离；Consumer Decoder/Observer、Transport 握手、恶意控制帧、关闭/取消竞态仍需专项失败测试，不允许机械改写成 `catch(Exception)`。
2. Checkstyle 对**是否存在 Javadoc**的语法检查，不证明中文契约、异常、线程、单位、资源所有权描述准确，仍需人工语义审查。
3. Windows Cursor/Codex 的 MCP Server 真机启动、npm 依赖版本锁定、MySQL 只读账号的实际 GRANT 检查尚未在用户端环境验证；用户授权后再执行。
4. Branch Protection / Ruleset 是否把 Java Quality 变成 GitHub 强制合并条件属于仓库管理权限变更，**未获授权不可修改**。
5. 固定硬件 p99/p99.9、JFR 与 10k 长稳属于另一个受控性能验收项目，不能用共享 Runner 的 Smoke 代替。

## 5. 恢复命令和中断保护

先检查 main、当前 Draft PR 的 Head/Base、相同 Head SHA 的 GitHub Actions。运行 JDK 21 Maven：

```bash
python3 scripts/check_project.py
python3 scripts/sync_agent_mcp.py --check
python3 scripts/test_agent_mcp.py
python3 scripts/test_java_conventions.py
python3 scripts/validate_repo_skills.py
python3 scripts/test_checkstyle_audit.py
mvn -B -ntp clean verify -Pquality
mvn -B -ntp -Pstyle-audit -DskipTests install
python3 scripts/summarize_checkstyle_audit.py \
  --json target/checkstyle-audit.json \
  --markdown target/checkstyle-audit.md \
  --enforce-zero
```

每次提交后按**新 SHA**核验 CI；单步等待超过十分钟无进展时，保留 Run/Job ID、最后错误、尚未验证项和恢复方式，结束当前等待，不进行无界重试。当前容器本地无法可靠下载外部 Maven 依赖时，以**相同提交**的 GitHub Actions 为构建证据；缺少证据就明确写未验证。

**未来 PR 的合并、删除分支、发布和生产数据操作仍需要用户单独明确授权。**
