# Peach RPC：Java 命名、Javadoc 与格式质量基线

> 状态：PR-8 Checkstyle **建议性扫描（Advisory）**。在统一历史代码前，不将全部旧问题作为阻断条件。本文件描述命令和范围，不预填任何未运行的统计结果。

## 1. 目的

源码层的 `scripts/check_java_conventions.py` 识别确定的禁用 API 和常见日志违规，但它不是完整 Java AST 解析器。PR-8 补充由 Checkstyle `3.6.0` 提供的 Java 语法树规则，生成**逐文件、逐行、逐规则**的历史违规清单，逐步实现可解释的全仓治理。

| 自动检查 | 当前规则 | 验收模式 |
|---|---|---|
| 类型命名 | `TypeName`：`UpperCamelCase` | 全仓 Advisory |
| 方法、普通字段、参数命名 | `MethodName`、`MemberName`、`ParameterName` | 全仓 Advisory |
| 常量命名 | `ConstantName` | 全仓 Advisory |
| Import | `AvoidStarImport` | 全仓 Advisory |
| 公共类型 Javadoc | `MissingJavadocType`（仅 public） | 全仓 Advisory |
| 文件可读性 | `LineLength` ≤120、`FileTabCharacter` | 全仓 Advisory |

这不验证方法 Javadoc 语义是否与参数、返回值、异常、线程和生命周期一致，也不检查英文日志有没有泄漏敏感数据。相关问题继续按照 Java Engineering Skill 和代码审查规则处理。

## 2. 命令及结果

从仓库根目录、JDK 21 下运行：

~~~bash
mvn -B -ntp -Pstyle-audit -DskipTests validate

python3 scripts/test_checkstyle_audit.py

python3 scripts/summarize_checkstyle_audit.py \
    --json target/checkstyle-audit.json \
    --markdown target/checkstyle-audit.md
~~~

Checkstyle 原始 XML 位于各 Maven 子模块的 `target/checkstyle-result.xml`。汇总器对未知根节点、缺失文件名、缺失报告直接失败，并把来源路径正规化后写入 JSON，避免导出本机绝对路径。GitHub Actions `Java Style Audit` 上传全部原始 XML 和汇总结果供按模块分派整改。

## 3. 为什么当前不把历史违规全部设为 Error？

Peach RPC 已经有 Public Core API、SPI、Wire v1 和自动生成代码，不能为了统一驼峰命名、Javadoc 或行长就批量更改 public 方法/字段。初次运行后的违规需要分成：

1. **确定的可机械修复项**：纯内部格式、单一文件中不影响公共契约的通配符 import、私有无意义字段名；
2. **需人工分析项**：公开 API、SPI、序列化与反射字段名、Record 分量、配置键、兼容性元数据；
3. **规则误报或有意例外**：自动生成源码、非常长的 URL/诊断模板、第三方签名约束；
4. **行为风险项**：异常处理、EventLoop、取消和资源关闭，必须单独测试/PR。

首轮报告用作整改台账，不声明“所有历史违规已修复”。确认整改范围和基线后，可以对**新增/修改代码**采用经验证的严格门禁，再分阶段提高全仓标准。

## 4. 与其他门禁关系

- `Java Agent Quality`：**已生效**的 changed-file 禁用 API/安全写法门禁与全仓 Regex/lexer Audit；
- `PeachRpcCoreArchitectureTest`：**已提交 PR-7** 的 ArchUnit 字节码依赖方向测试；
- `Java Style Audit`：本 PR 的 Java AST 命名/Javadoc/可读性测量，仅建议性；
- `mvn clean verify -Pquality`：真实代码编译、单元测试、严格 Javadoc 验证，继续是主质量门槛；
- Rolling Compatibility / 受控性能测量：按涉及的 Wire/Codec/Transport/Registry 变更触发，不能被任何静态风格扫描替代。

## 5. 验收与下一步

- [ ] 首次 GitHub Actions 执行成功，并上传非空结构的原始 XML / 聚合 JSON；
- [ ] 按 Checkstyle Rule、模块、文件统计存量违规；不给没有执行结果的模块填零；
- [ ] 评估每条误报和公开兼容限制；
- [ ] 为确定性可修复的存量问题分别提交整改 PR，公开接口/并发行为修改不能混在同一个格式 PR 内。

Checkstyle 自身是 Java 语法树检查，不是 API 兼容证明、数据流审计或性能分析器。
