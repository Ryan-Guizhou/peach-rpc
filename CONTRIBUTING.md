# Peach RPC 贡献与编码规范

Peach RPC 将低延迟、有界资源、兼容性和故障行为视为正确性的一部分。任何改动如果降低这些属性，即使 API 更短，也不应视为优化。

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
