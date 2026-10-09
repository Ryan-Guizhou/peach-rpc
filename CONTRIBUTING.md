# Peach RPC 贡献与编码规范

Peach RPC 将低延迟、有界资源、兼容性和故障行为视为正确性的一部分。任何改动如果降低这些属性，即使 API 更短，也不应视为优化。

## Agent 协作与远程权限

跨 Codex、Cursor 与其他 Agent 的统一约束参见 [AGENTS.md](AGENTS.md)；
本次治理的已确认边界、迁移和验证策略参见
[Agent 工程治理实施契约](docs/engineering/agent-governance-plan.md)。
MCP 的数据库默认禁用和用户授权边界见 [Agent/MCP 指南](docs/engineering/agent-mcp.md)。

提交 PR 不等于授权合并。所有技术事实、性能结论和 CI 状态应引用当前提交的验证证据；
不以“遵守最佳实践”代替可复现测试。

完整命名规则、禁用 API、中文标准 Javadoc、英文日志及 exceptions，请参阅 [Java 工程编码规范](docs/engineering/java-coding-standard.md)。

## 编码规则

- JDK 21，UTF-8，无 BOM。
- Java 使用 4 空格缩进，不允许 Tab、通配符 import 和行尾空格。
- Java 单行建议不超过 120 字符。
- 公共/受保护框架 API 必须使用标准 Javadoc，注释使用中文并描述契约，不描述显而易见的实现细节。
- 运行时日志统一使用英文 SLF4J 参数化消息。
- 不记录密码、Token、完整 RPC 参数和敏感 Provider 数据。
- 请求热路径禁止注册中心远程 I/O、SPI 扫描和配置解析。
- 用户业务逻辑不得直接运行在 Vert.x Event Loop。
- 新增队列、Map、Executor、inflight 结构时必须定义容量或明确生命周期边界。

## 必须执行的检查

```bash
python3 scripts/check_project.py
mvn -B -ntp clean verify -Pquality
```

涉及热路径的改动还需要执行对应 JMH 基准，并在 PR 中记录 JDK、CPU、操作系统、Payload、并发度、预热和测量参数。


## 1.0.x 兼容红线

Peach RPC 1.0.x 已冻结：

- Wire Protocol v1；
- Public Core API；
- Stable Type ID 规则；
- Schema Fingerprint v1；
- 已分配 Codec / Message Type；
- Registry Compatibility Metadata key。

涉及这些边界的改动必须先提供兼容方案，并通过 Rolling Compatibility。不能为了局部实现简化静默破坏旧 Consumer/Provider。

## 社区与安全

- 参与项目即应遵守 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。
- 安全漏洞按 [SECURITY.md](SECURITY.md) 私密报告，不使用公开 Issue。
- 用户可见行为变化必须同步 README 中英文和相关设计/配置文档。
