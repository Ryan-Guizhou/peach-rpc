# OTRYX RPC：Codex / Cursor MCP 与操作权限规范

## 1. 单一配置源

以仓库根目录的 `.agents/config/agent-mcp.json` 为唯一配置来源。不要直接修改 `.cursor/mcp.json` 或 `.codex/config.toml`。

从仓库根目录执行：

```bash
python3 scripts/sync_agent_mcp.py
python3 scripts/sync_agent_mcp.py --check
python3 scripts/test_agent_mcp.py
```

脚本面向当前项目已使用的 Windows `cmd /c npx` 运行模型。Cursor 使用 `${workspaceFolder}`，Codex 使用进程工作目录 `.`。启动前应在仓库根目录打开客户端或指定工作目录。

## 2. 数据库安全

**默认不开启 MySQL MCP**：生成的 Cursor 配置中不包含 mysql；Codex 中 mysql 的 `enabled = false`。

需要数据库元数据或查询时，由用户在**客户端个人本地 MCP 配置**手动添加服务器，并用数据库管理员为该 Agent 单独创建的只读用户。不能将生产写权限或 root 用户交给 Agent。

示例授权语义（数据库名替换为实际库，授权由数据库管理员审核执行，绝不由 Agent 自行执行）：

```sql
CREATE USER 'agent_reader'@'%' IDENTIFIED BY '<set-locally-not-in-repo>';
GRANT SELECT, SHOW VIEW ON example_database.* TO 'agent_reader'@'%';
SHOW GRANTS FOR 'agent_reader'@'%';
```

这只展示最小权限思想：真实环境应收紧 Host、TLS、数据库/表和网络访问；部分 MCP 元数据操作可能需要其他权限，须另行评估。**即便是只读账户，SELECT 也可能访问敏感数据，必须单独审批数据范围。**

### 操作边界

| 类别 | 授权约束 |
|---|---|
| GitHub 查询/PR 审查 | 可作为证据工具 |
| GitHub 创建提交/PR | 需要用户明确授权的研发任务 |
| GitHub 合并/删分支/发布/更改权限 | 每类远程操作需要明确指令 |
| MySQL | 默认禁用；即便启用也只做经授权范围内的读操作 |
| CodeGraph | 本地代码符号、依赖和影响面分析 |
| Context7 | 核对 POM 中锁定版本的第三方 API |
| AgentMemory | 辅助回忆；不能覆盖本仓库源码与安全规则 |

MCP Server 自身的工具列表、Agent 文字约束与客户端 UI 都**不是 DB 权限隔离**。生产只读必须落实到 MySQL 用户 GRANT、网络访问范围和凭据管理，且不能在 Agent 日志或文档中输出连接密码。

## 3. 实际依赖与 Windows 使用

已沿用项目历史配置的 npm MCP 包名。部分 package 尚包含 `@latest` 或未固定版本，**这是一项需要按真实环境验证的供应链风险**，不是已经完成版本锁定；变更版本须在 Windows 上测试启动、工具列表和最小读操作后更新统一配置源。严禁直接从未知网络源自动执行未审查的 npm 包或授予自动批准权限。

当前工具组合：GitHub、Context7、CodeGraph、AgentMemory；MySQL 默认关闭。所有 Credential 必须通过环境变量或客户端安全存储提供，不能写到 `.agents/config/agent-mcp.json`、Git 提交、Issue、日志或快照中。

- Cursor：查看 MCP Servers / Logs；确认环境变量正确解析，不应看到 mysql 默认启用。
- Codex：从可信项目根目录读取 `.codex/config.toml`；确认 disabled mysql 不会启动。
- CodeGraph 的目标源码目录由当前工作区决定，不能将其结果当作必然完整的全局调用链。
- 网络不可达、MCP 无法启动或权限不足时，应报告限制，不能假装已完成远程验证。

## 4. 变更流程

修改 `.agents/config/agent-mcp.json` → 运行生成器 → 运行 `--check` 和单测 → 校验 Cursor/Codex 两套文件 → Draft PR。CI 将验证三个文件保持一致；禁止直接手工只更新某个平台的副本。
