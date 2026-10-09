# Peach RPC Agent 共享能力

根目录 [AGENTS.md](../AGENTS.md) 是所有 Agent 的共同约束和授权入口；`.agents/skills/<name>/SKILL.md` 仅提供按需加载的工程知识、检查流程与模板；`.agents/evals` 存放用于回归的行为场景。

Cursor 的 `.cursor/rules` 和 Codex 的 `.codex/config.toml` 是平台适配，不能复制公共规则全文。MCP 配置以 `config/agent-mcp.json` 为唯一事实源，使用 `python3 scripts/sync_agent_mcp.py --check` 防漂移。
