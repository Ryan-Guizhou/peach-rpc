# OTRYX RPC：Java 命名、Javadoc 与格式质量基线

> 状态：PR-8 建立建议性扫描；**PR-13 在已验证的零违规基线上将同一 Checkstyle 规则集升级为严格 CI Gate**。历史扫描证据保留，严格门禁只覆盖当前配置实际检查的规则与 Maven Reactor。

## 1. 目的

源码层的 `scripts/check_java_conventions.py` 识别确定的禁用 API 和常见日志违规，但它不是完整 Java AST 解析器。PR-8 补充由 Checkstyle `3.6.0` 提供的 Java 语法树规则，生成**逐文件、逐行、逐规则**的历史违规清单，逐步实现可解释的全仓治理。

| 自动检查 | 当前规则 | 验收模式 |
|---|---|---|
| 类型命名 | `TypeName`：`UpperCamelCase` | 全仓 CI Gate（PR-13） |
| 方法、普通字段、参数命名 | `MethodName`、`MemberName`、`ParameterName` | 全仓 CI Gate（PR-13） |
| 常量命名 | `ConstantName` | 全仓 CI Gate（PR-13） |
| Import | `AvoidStarImport` | 全仓 CI Gate（PR-13） |
| 公共类型和方法 Javadoc | `MissingJavadocType` / `MissingJavadocMethod`（仅 public，测试目录豁免文档覆盖检查） | 全仓 CI Gate（PR-13） |
| 文件可读性 | `LineLength` ≤120、`FileTabCharacter` | 全仓 CI Gate（PR-13） |

这不验证方法 Javadoc 语义是否与参数、返回值、异常、线程和生命周期一致，也不检查英文日志有没有泄漏敏感数据。相关问题继续按照 Java Engineering Skill 和代码审查规则处理。

## 2. 命令及结果

从仓库根目录、JDK 21 下运行：

~~~bash
mvn -B -ntp -Pstyle-audit -DskipTests install

python3 scripts/test_checkstyle_audit.py

python3 scripts/summarize_checkstyle_audit.py \
    --json target/checkstyle-audit.json \
    --markdown target/checkstyle-audit.md \
    --enforce-zero
~~~

Checkstyle 原始 XML 位于各 Maven 子模块的 `target/checkstyle-result.xml`。PR-13 的汇总器会解析根 POM 和嵌套 `modules`，**要求每一个真实 Maven Reactor 模块都有对应 XML**，且其 `src/main/java` 和 `src/test/java` 下每个 `.java` 文件都包含在 Checkstyle 的来源清单中；缺失/额外模块报告、遗漏 Java 源码、异常来源路径、未知 XML 根节点或缺失文件名均会导致失败。汇总器把来源路径正规化后写入 JSON；``--enforce-zero`` 在报告生成后拒绝**任何严重级别**的 Checkstyle 违规（包括 `warning`），仍保留原始 XML 供排查。独立 `benchmarks/rpc-comparison` POM 不属于主 Maven Reactor，不被错误纳入该门禁。

## 2.1 首轮结果与规则修正

[PR-8 首轮 Java Style Audit](https://github.com/Ryan-Guizhou/otryx/actions/runs/37874810150) 成功，上传原始 XML 和聚合报告，扫描 **208 个 Java 文件、19 份 Checkstyle XML**；发现 **31 条 MissingJavadocType**，全部定位到 `src/test/java` 下的公开测试类或测试 fixture，`src/main/java` 没有该规则的缺失记录。此数字是**特定规则、特定提交、特定扫描范围**的历史基线，不意味着全仓 Javadoc 合格。

这些测试夹具存在 Public 类更多是为了 mock/反射/编译测试，机械补上“测试类”Javadoc 将制造大量无信息价值的注释。因此 PR-10 的规则调整为：

- `MissingJavadocType` **继续检查生产源码的 public 类型**；
- 新增 `MissingJavadocMethod` **检查生产源码的 public 方法和构造函数**，默认 `@Override` 继承语义与简单 Bean 属性访问器适用合理豁免；
- 仅对 `MissingJavadocType|MissingJavadocMethod` 在 `src/test/java` 中豁免，**测试代码的命名、Import、行长等其他检查继续执行**；
- 使用 Python 单测验证豁免正则能够命中测试目录而不命中 `src/main/java`。新的生产 API 统计必须等待 PR-10 Checkstyle Job 实际执行后填写。

测试代码确实需要解释复杂生命周期、协议样例或不安全的故障注入时，依然应写有意义的中文 Javadoc/注释；豁免的是**强制覆盖率**，不是豁免可读性要求。

Checkstyle 的所有 Style Audit 检查统一使用 **warning** 级别，避免历史建议性问题被 GitHub Actions 注记为红色 Error 而误导开发者；真正禁止的 API、EventLoop 不安全行为和 Core 依赖边界仍由 Java Agent Quality 和 ArchUnit 作为独立的硬性质量门禁。

## 2.2 第二轮 Checkstyle 结果与 JMH 基准整改

[PR-10 的新版审计](https://github.com/Ryan-Guizhou/otryx/actions/runs/37875213504) 再次扫描 **19 份 XML、208 个 Java 文件**，发现 **3 条 MissingJavadocMethod**。读取原始 `checkstyle-audit.json` 后，确认这 3 条**全部位于 `otryx-benchmarks/src/main/java/io/peach/rpc/benchmarks/ProtocolCodecBenchmark.java`**，分别是：

| 行号（整改前） | 方法 | 需要说明的契约 |
|---|---|---|
| 30 | `setup()` | Trial 开始前构造固定 RPC Frame 和预编码 bytes，初始化开销不计入吞吐 |
| 44 | `encode()` | 仅计 Wire v1 编码吞吐，返回每次编码的字节数组 |
| 49 | `decode()` | 仅计预编码字节的解码吞吐，不含传输/注册中心调用 |

**重要边界：** JMH Benchmark 虽然位于 `src/main/java`，但不属于用户可直接依赖的 OTRYX RPC 公开运行时 API。不能把这 3 条描述为“RPC 用户公共接口缺少 Javadoc”。本阶段在不改变方法签名、`@Setup`、`@Benchmark`、Codec 或 Wire v1 的情况下补充了有价值的中文基准说明，待本 PR 最新 CI 验证。由于检查器默认豁免测试目录，它**不是所有 Java 源码的注释语义全面审计**。

## 2.3 PR-13：从零违规证据升级到严格门禁

[PR-12 最新 Java Style Audit](https://github.com/Ryan-Guizhou/otryx/actions/runs/37875650253) 的聚合日志明确报告 **19 份 Checkstyle XML、208 个 Java 文件、0 条违规**。该数字来自固定提交和既有规则集，**不代表整个代码库不存在敏感信息泄露、线程竞争或 Java API 兼容问题**。

利用上述零违规基线，PR-13 将 **Java Style Audit** 的结果由仅上传 Artifact 改为：Maven 仍先生成完整报告，随后由 Python Gate 验证 Reactor 报告覆盖率并在发现任何违规时将 CI 置为失败。新的生产公共类、方法、命名、Import、行长违规由此无法绕过该检查；测试源码的命名、Import 和行长照常检查，但公共测试 Fixture 的机械 Javadoc 覆盖检查仍被排除。

Gate 只说明“Checkstyle 所配置且实际扫描到的规则无违规”。它不能替代 [Java Agent Quality](java-coding-standard.md) 的禁止 API 检查、ArchUnit、编译测试、运行时/协议回归和人工代码审查；也**不能把未经证明的日志/注释语义正确性等同于无检查告警**。

报告完整性通过遍历真实的 Maven `<modules>` 配置得到，而不是把“19 模块”永久写死为魔法常量。若新增模块却未执行 Checkstyle，对应 XML 缺失会使门禁直接失败。编译失败、Checkstyle 插件出错也不会伪装成“零违规”。

## 3. 为什么早期不直接将所有历史违规升级为 Error？

OTRYX RPC 已经有 Public Core API、SPI、Wire v1 和自动生成代码，不能为了统一驼峰命名、Javadoc 或行长就批量更改 public 方法/字段。初次运行后的违规需要分成：

1. **确定的可机械修复项**：纯内部格式、单一文件中不影响公共契约的通配符 import、私有无意义字段名；
2. **需人工分析项**：公开 API、SPI、序列化与反射字段名、Record 分量、配置键、兼容性元数据；
3. **规则误报或有意例外**：自动生成源码、非常长的 URL/诊断模板、第三方签名约束；
4. **行为风险项**：异常处理、EventLoop、取消和资源关闭，必须单独测试/PR。

首轮报告用作整改台账，不声明“所有历史违规已修复”。确认整改范围和基线后，可以对**新增/修改代码**采用经验证的严格门禁，再分阶段提高全仓标准。

## 4. 与其他门禁关系

- `Java Agent Quality`：**已生效**的 changed-file 禁用 API/安全写法门禁与全仓 Regex/lexer Audit；
- `OtryxRpcCoreArchitectureTest`：**已提交 PR-7** 的 ArchUnit 字节码依赖方向测试；
- `Java Style Audit`：PR-13 已启用的 Java AST 命名/Javadoc/可读性严格零违规门禁，并检查 Maven Reactor 报告完整性；
- `mvn clean verify -Pquality`：真实代码编译、单元测试、严格 Javadoc 验证，继续是主质量门槛；
- Rolling Compatibility / 受控性能测量：按涉及的 Wire/Codec/Transport/Registry 变更触发，不能被任何静态风格扫描替代。

## 5. PR-13 验收与下一步

- [ ] 当前 PR Head 的 `Java Style Audit` 工作流及 Python 正反向测试通过；
- [ ] 完整 Maven Reactor 每个模块都产出 XML，所有源码文件被扫描，聚合 JSON 的 `violations=0`；
- [ ] 检查门禁能拦截 `warning` 级别违规、模块缺失/额外报告与空报告；
- [ ] 验证 CI 主构建与已有 ArchUnit、Nacos/Transport 测试没有回归；
- [ ] 未来根据真实误报和公开兼容限制逐项调整规则并提供测试，不得直接放宽或跳过检查。

Checkstyle 自身是 Java 语法树检查，不是 API 兼容证明、数据流审计或性能分析器。
