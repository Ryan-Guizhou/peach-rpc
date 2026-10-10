# OTRYX RPC 1.0 首发前工程治理实施契约 V1.1

> 状态：用户已确认并授权实施，PR-01 已合并，PR-02 至 PR-07 分阶段执行。技术事实以最新源码、POM、测试与对应 Head SHA 的 CI 为准。

## Goal

构建简单易用、高性能、高并发、高可用的 Java RPC 框架；参照 Peach Cloud 完成全仓中文 Javadoc（类型级 @Author/@Version/@CreateTime）、英文 SLF4J LoggerFactory 日志、格式/命名、风险边界与自动化门禁的长期统一。

## Scope

覆盖手写生产源码、测试、独立 Benchmark/Comparison、Core、各能力族 Adapter、Starter/Examples，以及 AGENTS、共享 Skills、CI、Checkstyle、POM 与工程文档。

## Non-goals

不发布 Release；不修改已固定的 stable 分支；不改 GitHub 权限或生产数据；不在纯注释/日志/格式 PR 中重写协议、容错、并发或资源生命周期。无需为未发布旧版建立废弃 API 或兼容桥。

## Compatibility and correctness

项目为 com.peachsoft.otryx:otryx-rpc:1.0.0-SNAPSHOT，尚未 GA。历史 Peach RPC/OTRYX 开发版本不构成兼容性义务；API、SPI、Wire、Codec、Schema、注册中心元数据和配置可根据新设计直接调整，但必须分开设计与回归验证，保证当前版本自洽、Consumer/Provider 互通、安全及性能指标不退化。旧稳定分支仅作恢复证据，不随 main 更新。

## Constraints

- Java 21 / Spring Boot 3.5.4 / Maven 多模块，以现有 POM 为准。
- 类型元数据必须有来源，不以整改日期伪装创建日期；首次引入的 Git 提交仅能证明最早入库时间，不能证明实际首次编码时间。
- LoggerFactory 不统一替换为 Lombok；日志英文、参数化、脱敏，不在热路径增加逐请求 INFO。
- Core 依赖保持中立；EventLoop、Admission、Permit、ByteBudget、Future/CompletionStage 完成与关闭语义不可在纯规范 PR 中改变。
- Agent 的共享 Skill 统一位于 .agents/skills，Cursor/Codex 使用薄适配；远端写入与生产数据仍需独立授权。

## Plan

1. **PR-01 — 已合并：** 根项目 otryx-rpc、1.0.0-SNAPSHOT、Registry/Serialization/Transport/Proxy/Observability/Spring Boot 能力族与构建脚本路径整改。合并提交 541035e70ee57f48999b01408189d6f0a4e10894。
2. **PR-02 — 规范和质量基础设施：** Javadoc Plugin 自定义标签、Agent/Skill/编码规范统一、类型元数据全仓审计与测试、变更 Java 文件门禁；不宣称存量代码已经全部合规。
3. **PR-03 — Core：** API、SPI、协议、Consumer/Provider、核心运行时类型注释、日志与非行为风格改造；潜在行为缺陷另列专项 Issue/PR。
4. **PR-04 — Adapter：** Registry、Serialization、Transport、Proxy，覆盖元数据、日志、资源契约、配置规范。
5. **PR-05 — 支撑生态：** Observability、Spring Boot、Codegen、Examples。
6. **PR-06 — 测试与基准：** Tests、Benchmarks、独立 RPC Comparison，清理存量注释和日志规范缺口。
7. **PR-07 — 最终审计：** 全部手写类型元数据与命名/格式零违规，明确有审批的无法核实例外，检查完整 CI、Javadoc JAR、Examples、受控性能验证与中英文文档一致性。

实际工作量较大时允许拆分 PR，阶段编号不代表必须只用一个 PR。禁止跳过验证或把旧提交状态写成新提交通过。

## Verification

~~~bash
python3 scripts/test_java_doc_metadata.py
python3 scripts/check_java_doc_metadata.py --audit --report target/java-javadoc-metadata-audit.json
python3 scripts/check_java_doc_metadata.py --changed --base <base-commit-sha>
python3 scripts/test_java_conventions.py
python3 scripts/check_project.py
mvn -B -ntp -Pstyle-audit -DskipTests install
mvn -B -ntp clean verify -Pquality
~~~

PR-07 完成历史债务清理后，再把 --enforce-all 启用为无豁免的常规 CI 检查（有证据的例外应当可审计）。源文本检查不能证明作者真实性、Javadoc 语义、敏感数据流、线程安全或协议正确性，这些继续以审查与专项测试为准。

## Risks and rollback

- 自动补齐历史作者/时间会制造虚假信息：保留逐文件审计和来源核实流程。
- 过早全仓强制新标签会阻断所有无关 PR：PR-02 先审计存量、严格检查修改文件，后续全仓收敛。
- Log/Trace 脱敏可能削弱排障；必须保留安全错误类型、关联 ID 与受控诊断策略。
- 大范围格式化及 API 重构不得与纯规范改动混合；每个 PR 独立可回滚。
- 相关 Head SHA 的 CI 未全部通过前不得合并，合并后主分支若失败立即停止继续推进。
