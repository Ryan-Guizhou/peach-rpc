# Peach RPC Agent 工程治理：可恢复断点

> 更新时间：2026-10-09（Asia/Singapore）。**仅是提交时的快照。恢复任务须先查 GitHub 的最新 PR Head SHA、Base 和相同 SHA 的 CI。**
>
> 用户授权：仅 Peach RPC；全仓审计、编码规范/日志/注释/安全改进，按阶段提交 **Draft PR**。**未经额外指令严禁合并、删分支、发布、修改 main 或生产数据。** 单个外部等待超过十分钟无进展时停止当前等待，记录实际状态和下一步，不无限轮询。

## 1. 核心约束

- JDK21 / Maven / Spring Boot 3.5.4（以当前 POM 为准）。公开 1.0.x Java API、Wire v1、Codec 和 Message IDs、Stable Type IDs、Schema Fingerprint v1、SPI 与 Registry metadata 保持兼容。
- 中文标准 Javadoc，不复制 Peach Cloud 的 `@Author/@Version/@CreateTime`；`@since` 必须由真实发行历史证明。
- 英文 SLF4J 参数化日志，不输出 Token、Secret、完整请求或未经审查的业务 Throwable/堆栈；EventLoop 禁止阻塞；有界 Queue/Executor/Admission、取消和终态释放可验证。
- Cursor/Codex 以 `AGENTS.md` 和 `.agents/skills` 为共用行为事实源；MySQL MCP 默认禁用；只读真正依赖数据库授予的用户 GRANT，不依赖提示词承诺。
- **PR 只创建，不合并。** 研发事实、实际 CI、建议性质量审计、受控性能证据分开陈述。

## 2. 当前堆叠 PR 与验证事实

| 阶段 | PR | 主题与状态 | 证据/下一步 |
|---|---|---|---|
| 0 | [#27](https://github.com/Ryan-Guizhou/peach-rpc/pull/27) | Provider 就绪竞态；Draft | 主 CI/滚动/Release 已通过 |
| 1 | [#28](https://github.com/Ryan-Guizhou/peach-rpc/pull/28) | Agent/MCP 统一与权限；Draft | Agent Governance、CI 已通过 |
| 2 | [#29](https://github.com/Ryan-Guizhou/peach-rpc/pull/29) | Java 规范/4 Skills/禁用 API；Draft | Java Agent Quality 已通过，旧 SHA 在 Transport 测试有过偶发失败，见 #31 |
| 3 | [#30](https://github.com/Ryan-Guizhou/peach-rpc/pull/30) | Javadoc 和日志首批治理；Draft | 专项门禁通过，旧 SHA 重连测试偶发失败见 #31 |
| 4 | [#31](https://github.com/Ryan-Guizhou/peach-rpc/pull/31) | Drain 竞态/重连生命周期；Draft | 主 CI、Rolling、Java Agent Quality、Release 成功 |
| 5 | [#32](https://github.com/Ryan-Guizhou/peach-rpc/pull/32) | Core 边界/无界 Executor/日志规则；Draft | 主 CI、Java Agent Quality、Agent Governance、Release 成功 |
| 6 | [#33](https://github.com/Ryan-Guizhou/peach-rpc/pull/33) | Nacos 控制面中断与 Throwable 终态；Draft | CI、Nacos Chaos、Rolling 等全部成功 |
| 7 | [#34](https://github.com/Ryan-Guizhou/peach-rpc/pull/34) | ArchUnit 字节码依赖边界；Draft | CI、Rolling、Java Agent Quality 全部成功 |
| 8 | [#35](https://github.com/Ryan-Guizhou/peach-rpc/pull/35) | Checkstyle AST 建议性审计；Draft | CI、Style Audit、Rolling 成功；首轮 208 Java/19 XML/31 条测试类 MissingJavadocType |
| 9 | [#36](https://github.com/Ryan-Guizhou/peach-rpc/pull/36) | Provider 用户 Throwable WARN 脱敏；Draft | [Head aaa75b5d 的主 CI](https://github.com/Ryan-Guizhou/peach-rpc/actions/runs/37874991795) 已成功（需恢复时确认新提交） |
| 10 | [#37](https://github.com/Ryan-Guizhou/peach-rpc/pull/37) | 公共方法 Javadoc 规则与测试夹具豁免；Draft | [Head d0909f10 Style Audit](https://github.com/Ryan-Guizhou/peach-rpc/actions/runs/37875213504)：208 文件/19 XML/3 条 JMH benchmark 方法缺失 |
| 11 | [#38](https://github.com/Ryan-Guizhou/peach-rpc/pull/38) | 3 个 JMH 方法契约补充；Draft | [Head c05243c0 Style Audit](https://github.com/Ryan-Guizhou/peach-rpc/actions/runs/37875476842)：208 文件/19 XML/**0 条当前规则匹配**；其他 CI 以该 SHA 为准 |
| 12 | [#39](https://github.com/Ryan-Guizhou/peach-rpc/pull/39) | OpenTelemetry 原始 Exception Event 脱敏；Draft | 本断点记录所在分支；必须确认**最新 Head SHA** 的 OTel 测试、CI、Style、Rolling、Release |

**严格强调：** #29/#30 的失败运行是历史事实，后续 #31 在继承分支上解决了实际竞态，不能把后者的成功错误算给旧 SHA。#38 的 0 条匹配只覆盖 Checkstyle 当前规则，而非整个 Java 语义审查或可观测性数据流安全的证明。

## 3. 11 处 catch(Throwable) 的治理边界

[代码审计台账](java-existing-code-audit.md) 已对 Consumer 1、Provider 4、Nacos 控制面 1、Transport Client 3、Transport Server 2 逐项说明风险。

- 保留多数请求/连接边界捕获以保证 Future 终态结束，不机械改成 `catch(Exception)`。
- PR #33 已修复 Nacos 计划任务 `join()` 不可响应中断、静默吞控制面错误问题，并补 User Error / CompletionStage Error 的终态回收测试。
- 仍需：Consumer 解码/Observer 回调的故障隔离、Transport 异常握手/恶意控制帧、服务关闭/取消竞态的专项测试；**OOM/ThreadDeath 等 JVM 致命错误策略尚未决定**。

## 4. 待完善工作（不能算作已完成）

1. **PR #39 当前 Head 的 CI 结果**：检查 [GitHub PR #39](https://github.com/Ryan-Guizhou/peach-rpc/pull/39) 与实际 Run，若失败优先读 Job Logs 修复；一个外部步骤持续卡住超过十分钟应保存 SHA/Run/Job，结束当前等待。
2. 复核 OpenTelemetry Span Event 脱敏及 Provider Warn 脱敏实际测试。**自定义 RpcObserver 仍接收原始 Throwable**，需要进一步用户自定义扩展审查和部署说明。
3. 检查 Java Code Style Audit 对 src/main 的公共方法覆盖，按实际报告安全补注释。已知 #38 Checkstyle 为 0 匹配，但不证明 Javadoc 描述与代码一致；不为覆盖率生成重复无用注释。
4. 对已有公共类型、Record、Spring 配置、Stable ID、历史命名做 **语义级契约审查**，有行为风险或破坏二进制兼容必须单独设计/PR。
5. Windows Cursor 与 Codex 实机 MCP 握手、npm 版本锁定/供应链核查需要用户端设备与权限；本环境不能假装已验证。
6. GitHub Branch Protection / Ruleset **未经用户授权不得修改**；若需让所有 Agent Quality Gate 成为强制合并条件，需要单独授权。
7. 任何性能敏感改造的固定硬件 JMH/JFR、真实 p99/p99.9 与 10k soak 仍需受控证据，不可用 Shared Runner Smoke 替代。

## 5. 恢复步骤与当前工具约束

优先使用 GitHub Connector 查询：
```text
Repo: Ryan-Guizhou/peach-rpc
Main: https://github.com/Ryan-Guizhou/peach-rpc
PR stack: #27 → #28 → #29 → #30 → #31 → #32 → #33 → #34 → #35 → #36 → #37 → #38 → #39
Latest: #39, branch fix/agent-quality-12-otel-exception-redaction
```

在 JDK 21 Maven 环境运行：
```bash
python3 scripts/sync_agent_mcp.py --check
python3 scripts/test_agent_mcp.py
python3 scripts/test_java_conventions.py
python3 scripts/validate_repo_skills.py
python3 scripts/test_checkstyle_audit.py
python3 scripts/check_java_conventions.py --audit --report target/java-conventions-audit.json
mvn -B -ntp clean verify -Pquality
mvn -B -ntp -Pstyle-audit -DskipTests install
python3 scripts/summarize_checkstyle_audit.py --json target/checkstyle-audit.json --markdown target/checkstyle-audit.md
```

由于本地容器无法可靠连接 GitHub Maven 依赖仓库，真实可复现 Maven 构建结论以对应 Head SHA 的 GitHub Actions 为准；没有执行的测试必须明确写“未执行”，不声称通过。

**恢复时先核对最新 HEAD 与 CI；发现新提交/变基后以前通过的运行记录不计为本提交通过。** 任何等待超过十分钟不得无限轮询、重复创建 PR 或自动合并，保存断点并报告问题。
