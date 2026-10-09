# Peach RPC 现有 Java 代码治理：审计与整改台账

> 状态：PR-3 第一批整改，**不是宣称所有 Java 代码已经完全合规**。以本 PR 的最新 CI 和审查结果为准。主分支尚未合并这些更改。

## 1. 已完成的全仓机器审计

PR-2 [Java Agent Quality workflow](https://github.com/Ryan-Guizhou/peach-rpc/actions/runs/37757406984) 对 **212 个 Java 文件**执行 `--audit`，结果为：**0 个错误级匹配、11 个需人工审查的 `catch (Throwable)` 告警**。该审计仅覆盖明确规则集，不等于深入语义与敏感信息数据流证明。

首次规则扫描中曾出现 8 条假阳性：5 条 SLF4J 的**编译期常量字面量拼接**和 3 条 `tools/rpc-comparison` 命令行测试输出。经复核后，Lint 只禁止真正的运行时动态日志拼接，并排除独立 Benchmark CLI，新增了回归测试。不能因为扫描 0 错误就把人工审查清空。

## 2. 第一批已修改源码（不改公开契约）

| 文件 | 改动 | 兼容边界 |
|---|---|---|
| `PeachRpcClient.java` | 补充 Consumer 回调线程、执行器及关闭资源的中文类契约 | 无公开 API 修改 |
| `PeachRpcServer.java` | 补充 STARTED 就绪条件、Admission 预算和 Lease 释放责任 | Wire v1 不变 |
| `RpcProtocolCodec.java` | 将二进制 Header/兼容冻结边界写入类 Javadoc | 不改协议字节 |
| `RpcProviderAdmissionOptions.java` | 修正 Javadoc 中非标准反引号引用为 `{@code}` | Record 组件不变 |
| `ExtensionLoader.java` | 澄清按名称缓存、线程/连接所有权与热路径 SPI 发现边界 | SPI 名称不变 |
| `ForyRpcCodec.java` | 澄清 Trusted 兼容默认与严格白名单生产迁移边界 | Codec ID 不变 |
| `PeachRpcProperties.java` | 补充真实配置作用域、默认值校验责任 | 配置键不变 |
| `VertxRpcTransportServer.java` | 补充帧所有权、写队列及关闭契约；结构化告警带帧长度与协商上限 | 仅注释和日志 |
| `EtcdRegistry.java` | 将恢复/健康探测日志统一为简洁稳定的英文事件句式 | 注册中心逻辑不变 |
| `VertxRpcTransportClient.java` | 明确“无 Pending Response”英文日志字段 | 传输逻辑不变 |

这批修改没有改动 public 方法签名、Codec、Frame Parser、JDK 行为或并发逻辑。不能把增加 Javadoc 等同于修正运行时潜在竞态。

## 3. `catch (Throwable)` 待确认事项（11 处）

以下是强制人工复核列表，**目前保留原有行为**：

| 模块/类型 | 机器扫描处数 | 要确认的风险 |
|---|---:|---|
| `PeachRpcClient` | 1 | 解码/用户完成回调的异常归属、致命 `Error` 是否应传播 |
| `PeachRpcServer` | 4 | 用户业务异常隔离、异步执行完成、失败响应构造与回收 |
| `NacosControlExecutor` | 1 | Control Executor 异步边界对 `Error` 与 CompletionStage 的处理 |
| `VertxRpcTransportClient` | 3 | EventLoop/socket 错误隔离与连接级 failAll 语义 |
| `VertxRpcTransportServer` | 2 | 传输回调/应答路径的异常隔离及关闭状态 |

`catch(Throwable)` 不应机械改成 `catch(Exception)`：在异步隔离边界，改变捕获范围可能导致 Promise/Future 不完成、资源或等待者泄漏；同时吞噬 JVM 致命错误也有风险。需要逐个覆盖异常、取消、回调执行器拒绝与 ByteBudget 归还的测试，再决定是否收窄或另行传播。这些属于**需单独行为 PR**的风险，不能混在纯 Javadoc 整改中。

## 4. 命名与架构审查

- 核心 `PeachRpcClient`、`PeachRpcServer`、`RpcProtocolCodec` 和 `PeachRpcProperties` 较大。进一步拆分需要先证明职责/状态机边界以及 JMH/回归影响；不得因为文件超过某行数就机械分拆。
- Public Core API、`record` 组件、Starter 配置键、SPI 名、Schema Fingerprint/Wire 字段已被 1.0.x 兼容约束冻结；即使名字可以更清晰，本轮**不重命名**。
- 内部类型/私有方法的下一批重命名应在 CodeGraph/引用分析后实施，并保留无反射/配置引用及测试证据。
- 现有静态脚本不是完整 Java AST / 命名规则/敏感数据分析器。规范中“必须人工审查”的规则继续作为 Code Review Checklist，不制造不存在的自动化保证。

## 4.1 异常边界逐项处置（PR-6）

这 11 个 `catch (Throwable)` 不是 11 个可机械替换的编译问题。下表根据源码对请求/控制面执行生命周期逐项分类，**只把有证据的行为变动放入本阶段**。

| 位置 | 处理决定 | 理由和需要的测试 |
|---|---|---|
| `PeachRpcClient.completeTransportResponse` | 保留；后续补 Codec 失败 / observer 故障 / 取消与 retry 的组合测试 | Consumer 完成任务必须完成 Future / 维护 endpoint 计数；简单改 catch 范围会让调用长期挂起 |
| `PeachRpcServer.execute` | 保留，新增用户方法抛出 `AssertionError` 后返回失败并释放准入许可的测试 | 业务执行边界不能导致在途 Future 永久挂起 |
| `PeachRpcServer.completeAsyncInvocation` | 保留，验证 CompletionStage 出错后请求生命周期 | `stage.toCompletableFuture()` 和完成回调注册均可能抛出不可预期错误 |
| `PeachRpcServer.completeAsyncInvocationResult` | 保留，新增异步 `CompletionStage` 异常结束、Frame 错误响应及许可释放测试 | 必须覆盖 metadata scope、编码及业务完成异常 |
| `PeachRpcServer.completeInvocationFailure` | 保留 | 构造错误响应本身也可能失败，此时必须 `completeExceptionally` 而非泄漏 Future |
| `NacosControlExecutor.submit` | 保留并修复中断处理 | 接收方必须获得 `Error`、`Exception` 的异常完成信号；`InterruptedException` 重新标记中断 |
| `VertxRpcTransportClient.handleGoAway` | 保留；后续专项 Buffer/close/failAll 测试 | EventLoop 协议解码不能使连接处于半失效状态 |
| `VertxRpcTransportClient.handleHandshake` | 保留；后续专项握手异常 / pending 请求结束测试 | 不合法 HELLO_ACK 必须使 handshake Future 异常完成 |
| `VertxRpcTransportClient.handleHeartbeat` | 保留；后续专项控制帧和重连测试 | 心跳解析异常必须关闭连接、回收待处理请求 |
| `VertxRpcTransportServer.handleHello` | 保留；后续 malformed HELLO / Observer 异常隔离测试 | Provider handshake 协议错误不得逃出 EventLoop |
| `VertxRpcTransportServer.handleHeartbeat` | 保留；后续 malformed PING / close 测试 | 维护连接超时及错误关闭策略 |

### Nacos 周期任务的实际缺陷

此前 `NacosControlExecutor.scheduleWithFixedDelay` 使用 `CompletableFuture.join()` 等待并静默捕获 `RuntimeException`。本 PR 将其改为**可中断**的 `get()`；关闭时在 `InterruptedException` 恢复当前线程中断标识，并结束本次周期任务，而非无条件继续重试。失败/成功转换通过一次 WARN、连续失败 DEBUG、恢复 INFO 记录，避免频繁失败产生 WARN 风暴。

新增测试证明用户提供的控制面动作抛出 `AssertionError` 后其 Future 会异常完成并可执行下一任务，且一次周期任务失败不会永久停止后续 reconcile。Provider 同时补充同步业务 `Error` 和异步 Future `Error` 的终态与 Admission 回收测试。

**仍待设计：** 无论异步边界用 `catch(Throwable)` 还是 `catch(Exception)`，`OutOfMemoryError`、`ThreadDeath` 等真正不可恢复 `Error` 的处置策略需要单独论证（例如先完成资源清理再交给 UncaughtExceptionHandler），本阶段不擅自改变现有协议错误分类。即使完成上述测试，也不等于证明任意 fatal Error 下 JVM 可继续服务。

## 5. 验收与剩余工作

应使用 PR-3 最新提交重新运行：

```bash
python3 scripts/check_project.py
python3 scripts/sync_agent_mcp.py --check
python3 scripts/test_agent_mcp.py
python3 scripts/test_java_conventions.py
python3 scripts/check_java_conventions.py --audit --report target/java-conventions-audit.json
mvn -B -ntp clean verify -Pquality
```

- 尚待：对 11 处 `catch(Throwable)` 分别编写针对性异常生命周期测试与处置；不能一概变更行为。
- 尚待：更完整的公共 API Javadoc/配置与日志字段语义清查，以及敏感日志数据流、依赖漏洞、AST 规则和结构复杂度检查。
- 尚待：若触及并发/协议实现，执行受控端到端 p99/内存/10k 稳定性验证。纯 Javadoc 与日志文字改造不应产生协议变化。

本台账保留未完成项供后续按模块开独立 PR，而不是把缺口隐藏在“已全仓治理”的措辞中。
