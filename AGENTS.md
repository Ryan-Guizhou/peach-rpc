# Peach RPC — Repository Agent Contract

本文件为 Cursor、Codex 和其他 Agent 的统一工作入口。唯一事实源：当前分支源码、测试、POM、构建结果 > 当前依赖版本官方文档 > 仓库技术文档和 CONTRIBUTING > 共享 Skills > 运行时适配 > 历史对话。不得根据旧资料声称某功能已实现。

## 工作引擎与授权

Task Intake → Source/Impact Analysis → Complexity Gate → Implementation Contract (复杂任务) → 用户确认 → Implement → Verify → Draft PR → 用户决定是否合并。

- 简单、确定且不涉及公共契约的局部修复可直接实施。
- 公共 API、Wire/Codec/Schema、SPI、配置、线程/资源、鉴权、安全、模块架构、兼容性重构属于复杂任务。实施前交付 Goal、Scope、Non-goals、Compatibility、Constraints、Migration、Verification、Risks，获用户确认方可执行。对已授权的当前任务不重复确认。
- 本次任务授权开发并提交 Draft PR，不等于授权合并、直接修改 main、发布、删除远端资源、变更权限或执行数据库写操作。这些动作均需要另行明确授权。
- 分阶段小 PR；逐阶段更新变更范围、运行证据、未完成项，禁止用已通过的旧提交 CI 代替新提交验证。

## 中断保护与断点续写

- 单个 Maven、CI、远程 MCP 或其他待完成步骤持续无进展超过 **10 分钟**时，停止等待并中断可安全中断的本地操作。对于 GitHub 远端工作流，只记录当前 run/status，不为缩短等待擅自取消其他人的 Job。
- 中断时记录：任务目标、最后一个成功步骤、PR/分支/Head SHA、未通过的检查、日志/错误、下一条可执行命令、回滚方式和影响边界。
- 断点记录存放于 `docs/engineering/agent-governance-checkpoint.md` 或当前 PR 描述；恢复时必须重新查询最新 SHA 与 CI，不依据旧的对话状态盲目重复提交。
- 对确定性的代码缺陷先修复；遇到网络/工具访问失败最多做有限重试，仍不可用时如实报告，不循环等待、不伪造测试通过记录。
- 如当前步骤阻塞，但有无依赖的工作可开展，允许先处理独立任务并标记阻塞项；**不能将待完成工作描述为已完成**。

## 工程和兼容性红线

- JDK 21、Maven 多模块、Spring Boot 3.5.4；以实际 POM 为准。4 空格、UTF-8、LF、无通配符 import，参见 .editorconfig、[Java 编码规范](docs/engineering/java-coding-standard.md) 与 CONTRIBUTING.md。
- 1.0.x 冻结 Wire Protocol v1、Public Core API、Stable Type ID、Schema Fingerprint v1、Codec/Message IDs、Registry Compatibility Metadata。**禁止以风格/命名修复为由改变公开签名、Record 字段、序列化行为及兼容键。**
- 中文 Javadoc/必要行内注释、英文 SLF4J 参数化日志；Peach RPC 仅使用标准 Javadoc 标签（`@since` 必须真实），不复制 Peach Cloud 自定义 `@Author/@Version/@CreateTime`。
- 公开 API/SPI、Starter、Registry、Codec、Transport 的契约必须明确 null、异常、生命周期、线程归属、背压/取消、资源所有权。不要为注释覆盖率制造无意义注释。
- Core 不允许直接依赖 Vert.x / Nacos / Etcd / Fory / Spring 等实现；第三方技术通过 SPI/Adapter。Core 字节码层依赖规则见 [ArchUnit 架构门禁](docs/engineering/architecture-guardrails.md)，使用 Maven 测试自动执行。
- EventLoop 不执行阻塞 IO、Thread.sleep、同步等待 Future 或可任意阻塞的业务回调；Consumer 完成 Future 时也要检查同步回调的执行线程。
- RPC 热路径不得做服务注册中心 IO、SPI 扫描、无界排队或动态配置解析。
- 新 Executor、队列、连接、Pending Map、inflight bytes 和缓存必须明确有界、拒绝、取消、超时、关闭和资源释放。
- 不吞异常、不忽略 InterruptedException，不重试未声明幂等操作；日志和异常不泄露 Token、密码、私钥、完整 RPC 参数、签名 URL 或敏感 Metadata。

## MCP 与权限

MCP 为证据补全工具，不是无限授权。
- CodeGraph 用于 symbol/caller/impact；Context7 仅用于依赖版本不确定的 API；GitHub 用于已授权 PR、Issue、CI 和远端读写；AgentMemory 不得覆盖仓库事实。
- MySQL 默认**不连接**：只有使用数据库真正授权的独立只读账号，并经用户主动启用后才允许访问；prompt 或 MCP 配置本身不能强制只读。不得使用有生产写权限的用户。
- GitHub 合并、删除分支、Release、数据库 DML/DDL、权限变更和生产配置写入，都必须取得对应操作的明确授权。
- 不允许 Agent 自动批准未知第三方 MCP 或将凭据提交到源码。详见 docs/engineering/agent-mcp.md（本治理阶段加入）。

## Skills 与适配

唯一共享目录 `.agents/skills/<name>/SKILL.md`，不复制到 .cursor 或 .codex。只加载与任务相关的 Skill：
- `using-peach-rpc-java-engineering`：命名、Javadoc、日志、基础代码结构。
- `using-peach-rpc-compatibility`：Wire v1、Schema、SPI、公开 API 与迁移。
- `using-peach-rpc-performance`：EventLoop、Admission、背压、JMH/JFR。
- `review-peach-rpc-changes`：变更审查、违规分类、证据复核。
- 文档变化需同步 README.md 与 README.en-US.md 的相关结构和事实。

`.cursor/rules` 仅放薄入口，`.codex/config.toml` 仅放平台配置。不得出现多个互相漂移的规范事实源。

- Java 命名/类型 Javadoc/通配符 Import 与行长的 AST 审计见 [Checkstyle 建议性基线](docs/engineering/java-style-audit.md)，使用 `mvn -B -ntp -Pstyle-audit -DskipTests install`。历史问题只计入台账，不能直接改 public 兼容名称。

## Verification

- 通用门禁：`python3 scripts/check_project.py`；`mvn -B -ntp clean verify -Pquality`。
- Agent/MCP：`python3 scripts/sync_agent_mcp.py --check` 与 `python3 scripts/test_agent_mcp.py`。
- Wire/Registry/Transport：相关单元与集成测试、Rolling Compatibility；性能改动另做 JMH/JFR 和可复现 Evidence。shared runner 的数值不等于生产容量结论。
- 人工检查不可自动静态证明的规则，记录例外原因；不为保持 CI 绿色关闭必要检查。
- 最终只陈述实际改动、PR、测试通过/失败与剩余风险。
