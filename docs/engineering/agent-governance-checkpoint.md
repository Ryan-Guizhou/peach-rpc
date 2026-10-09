# Peach RPC Agent 工程治理：可恢复断点（Mainline）

> 更新时间：2026-10-09（Asia/Singapore）。本文件是**提交时的状态快照**。下一轮恢复时应以 GitHub 最新 `main` / PR Head / 同 SHA 的 Workflow 状态为准，不沿用静态结论。用户要求：外部 CI/MCP 持续阻塞超过十分钟就停止等待，记录现场后从断点续写。

## 一、主分支和稳定快照

- 仓库：[Ryan-Guizhou/peach-rpc](https://github.com/Ryan-Guizhou/peach-rpc)。
- 已完成并合并 PR #27–#39：当前 Agent Governance、MCP、四个 Skills、Java API/命名/日志/Javadoc、ArchUnit、建议性 Checkstyle、Provider/OTel 异常脱敏与回归测试等。
- **冻结稳定快照**：[stable/agent-quality-2026-10-09](https://github.com/Ryan-Guizhou/peach-rpc/tree/stable/agent-quality-2026-10-09) → `8f051b78d74978c5417394ed2cd22467e077c15f`。创建源为 #27–#39 合并后的 `main`，同 SHA 的 [主 CI](https://github.com/Ryan-Guizhou/peach-rpc/actions/runs/37880262195) 与 [Agent Governance](https://github.com/Ryan-Guizhou/peach-rpc/actions/runs/37880262218) 成功。
- **最新新增合并**：[PR #40](https://github.com/Ryan-Guizhou/peach-rpc/pull/40) 已经 Merge 到 `main`；Merge commit 为 `d78be62c991689b4977b5d8f3b6868a8099c23ad`。其 Head `4e442799ea8c51b3810a42d165a904d23ed89491` 上的主 CI、Java Style Audit、Agent Governance、Java Agent Quality、Release Readiness 均成功。
- **主线合并后检查**：截至编辑时，该 merge SHA 的 [Java Style Audit](https://github.com/Ryan-Guizhou/peach-rpc/actions/runs/37890054279) 和 [Agent Governance](https://github.com/Ryan-Guizhou/peach-rpc/actions/runs/37890054278) 成功；[main CI #37890054258](https://github.com/Ryan-Guizhou/peach-rpc/actions/runs/37890054258) 仍运行中，必须等实际结果，不能先写 SUCCESS。
- stable 是固定回退基线；后续治理只修改 `main`，不得反向将新 PR 合到 stable、移动 stable 引用或强推。
- 仓库上是否设置 GitHub Ruleset/Branch Protection **未获单独授权**，不能自行改变管理员权限；稳定分支只保证快照引用在本次创建后的初始 SHA，不构成强制不可变的服务器策略。

## 二、当前新增授权与工作流

2026-10-09 用户明确要求：**将当前已完成代码保留为 stable，后续提交直接合并 main**。

范围仅覆盖已确认的 Peach RPC Agent 质量治理工作，不包括独立发布、删除分支、生产数据写入、管理员权限变更或突破 Wire v1/Public Core API 兼容边界。采取可追踪的 **Mainline PR → 当前 Head CI 通过 → 合并 main → 再查 main CI**；新 PR 的 Base 必须是 `main`，不再从旧 Draft PR 堆叠。

合并后 main CI 未完成或失败时，停止下一阶段合并并调查，必要时提交单独 revert/fix PR；禁止用旧 PR 的绿色结果冒充最新提交通过。

更多说明见：[稳定基线与 Mainline 交付](stable-baseline.md)、[实施契约](agent-governance-plan.md)、[AGENTS.md](../../AGENTS.md)。

## 三、实测已完成的门禁与剩余问题

目前治理能力包括：

- `config/agent-mcp.json` 唯一 Cursor/Codex MCP 事实源；MySQL 默认未启用，真正只读依赖数据库授权。
- 4 个共享 Skills，`AGENTS.md` 工程角色/审批/兼容约束。
- 高置信度 Java 禁用 API 和日志文字门禁，ArchUnit 模块依赖边界。
- Checkstyle 对完整 Maven Reactor 的公共 Javadoc、命名、Import、行长等规则实现 **零违规的严格 CI Gate**（仅在配置的规则范围内）。
- Provider 与 Registry 的部分异常隔离/线程中断、Transport Drain 恢复、日志与 OTel 异常消息脱敏。

仍待：

1. **消费者** Decode/Observer 线程故障与回调异常隔离测试；**Transport** HELLO/HELLO_ACK/GO_AWAY/心跳畸形帧与关闭/取消资源边界专项测试。
2. 中文 Javadoc 的语义准确性、异常/生命周期/ByteBuffer 所有权与实际实现的一致性；AST Javadoc 存在性检查不能代替此审查。
3. Windows Cursor/Codex MCP 实际握手、npm MCP 版本锁定/供应链及 MySQL 真实只读 GRANT 验证；没有用户设备/凭据不能假装已通过。
4. 热路径性能、端到端 p99/p99.9、JFR allocation、10k 并发长稳仍缺独占受控硬件 Evidence，不能以短暂 CI Smoke 证明。
5. Branch Protection / Rulesets 若要作为硬性合并策略，需要用户单独明确授权。

## 四、恢复与停止规则

恢复时先核对：

1. stable branch SHA 是否仍是 `8f051b78d74978c5417394ed2cd22467e077c15f`。
2. main 最新 SHA 和对应 push CI 是否成功；若运行中先记录状态，不合并下一 PR。
3. 是否存在与 `main` 相比变动的开放 PR；每个 PR 的 Base、Head、Mergeable 与同 SHA 的所有相关检查。
4. 任何断点必须包括最后成功提交、GitHub Run/Job/日志、仍未执行的检查、下一步和必要回滚方式。

本项目的常用核验命令（JDK 21）：

~~~bash
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
  --markdown target/checkstyle-audit.md --enforce-zero
~~~

单项 CI/MCP/Maven 等远程步骤持续无进展超过十分钟：不无限轮询、不擅自取消他人作业，不宣称成功；保存具体阻塞及下一命令，并结束本轮等待或继续无依赖的其他可安全任务。
