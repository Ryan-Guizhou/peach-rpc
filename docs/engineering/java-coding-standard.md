# Peach RPC Java 工程编码规范

**状态：PR 阶段引入的正式建议；CI 强制范围见“门禁和例外”。**
**基线**：Java 21、Spring Boot 3.5.4、Maven、Wire v1（以当前 POM 和源码为准）。本规范吸收 Peach Cloud 的高信息量中文 Javadoc、英文日志、并发/资源契约思想，但本仓库不用其自定义类型头。

## 1. 优先级

正确性、安全、公开兼容性、可理解性、性能、可维护性、风格一致性。不要为了统一名称或缩短代码而破坏冻结的 Public Core API、Stable Type IDs、Wire v1、Schema Fingerprint 或配置键。所有行为改变先跑受影响测试。

### 代码结构

- `peach-rpc-core` 只承载 API、协议、中立策略、编排和 SPI；第三方依赖落在 Adapter 模块，不向 Core 反向依赖 Vert.x/Nacos/Etcd/Fory/Spring。
- Transport 负责网络帧及缓冲区所有权；Codec 负责序列化校验；Registry 负责注册发现/订阅；Runtime 负责准入、执行和结果；Starter 负责 Spring 装配。
- 不为类行数机械拆分；优先分析变更原因、职责凝聚力、调用关系、生命周期、共享锁与可测性。
- 优先显式依赖与不可变快照；Spring Bean 采用构造器注入；不要为了“现代化”无理由转换为 `record`、`var`、Stream、Optional 或反射代理。
- 不允许随意新增 `common` / `misc` / `helpers` 这种无领域边界的收纳模块；算法/策略先选择已有明确的包和 SPI。

架构分层不仅靠源码扫描：Core 生产类与 Public API 的依赖方向还由 [ArchUnit 字节码测试](architecture-guardrails.md) 保护。新增 Java 基础设施依赖前需确认 Core 无逆向依赖；规则以 Maven 自动测试为准。

## 2. 命名

| 目标 | 必须/推荐 | 避免 |
|---|---|---|
| Package | 小写、领域/职责层次，`io.peach.rpc.transport.vertx` | `util2`、`temp`、`misc` |
| Class / Interface | `UpperCamelCase`、名词或明确能力：`RpcMethodCodec`、`RegistrySubscription` | 无意义 `I` 前缀、`DataManager` |
| SPI 实现 | `<Technology><Contract>` 或明确策略名：`NacosRegistry`、`VertxRpcTransportFactory` | `DefaultImpl2`、`BaseUtils` |
| Method | `lowerCamelCase`、动词 + 领域名：`resolveEndpoint`、`tryAcquire` | `handle` 滥用、`doStuff` |
| Query/command | `find...` (可能不存在)、`require...` (找不到抛错)、`register...`、`release...` | `get...` 同时包含 IO/副作用却未声明 |
| Boolean | `is...`、`has...`、`can...`、`supports...` 与 Java Bean 约定一致 | 含义相反、双重否定 |
| Number/unit | `timeoutMillis`、`latencyNanos`、`maxInflightBytes`、`maxConcurrent` | `time`、`size`、`limit` 模糊 |
| Constant | `UPPER_SNAKE_CASE`，带范围与单位 | 匿名 magic number / 重复字符串 |
| Error | `RpcOverloadedException`、`RpcUnavailableException`，区分原因 | `SystemException` 之类含糊类型 |
| Tests | `<behavior>Should<observableOutcome>` 或中文明确的测试 Javadoc | `test1`、不能解释目的的断言 |

例外：已经公开的 Java/Record/注解成员即使命名欠佳，在 1.0.x 只能保留兼容（可新增非破坏性别名并安排后续大版本迁移）。RPC ID 和协议键绝不能因为编码风格更改。

## 3. Javadoc 与注释（中文）

适用类、接口、Record、注解、配置和非平凡 API。第一句描述**能力或契约**，而不是“XX 方法”；在调用方需要时说明 `null`、单位、顺序/可变性、异常条件、线程安全、阻塞/超时、幂等、取消、关闭、资源所有权和兼容要求。简洁是质量原则，不为覆盖率机械补注释。

- 使用标准 `@param`、`@return`、`@throws`、`@since`、`@deprecated` 和 `{@code}` / `{@link}`；`@since` 必须由 Git 历史/发行信息证明，查不到就不填。
- 不使用 Peach Cloud 的 `@Author`、`@Version`、`@CreateTime` 自定义标签；不凭当前时间伪造创建时间。
- 公共 API 更改须同步签名、方法 Javadoc、测试、调用方和文档；接口覆写不复制整段内容，无额外语义可使用 `{@inheritDoc}`。
- 内部注释解释“为什么”和安全边界（例如避免 EventLoop 阻塞、Lease 原子释放），不解释每个 if/赋值。
- Record 构造参数 Javadoc 应对应真实组件；代码语法/泛型版本以 Java 21 为准。

```java
/**
 * 从本地快照查找指定服务的端点。
 *
 * <p>此调用不访问远程注册中心；结果顺序不代表负载均衡优先级。</p>
 *
 * @param serviceKey 已标准化的服务键，不允许为 null
 * @return 端点快照；未发现时为空集合
 * @throws NullPointerException serviceKey 为 null 时抛出
 */
List<RpcEndpoint> findEndpoints(ServiceKey serviceKey);
```

以上是契约写法示意，并非声明仓库存在该方法。不要凭模板许诺线程安全或异常，必须以实际实现为准。

## 4. 日志（英文）

- SLF4J 参数化：`LOGGER.warn("RPC request rejected. serviceId={}, reason={}", serviceId, reason);`，字段名稳定，异常对象最后一个参数；记录实际发生的事件。
- 禁止日志拼接、异常重复打印、敏感 Header/Metadata、完整请求/响应、访问密钥、密码、Token、签名 URL；响应结果日志记录状态与受批准标识即可。
- `error` 代表需干预的失败，`warn` 代表非预期但可控的降级/拒绝，`info` 用于生命周期变化，热路径 per-call 正常处理通常不记录 info，`debug/trace` 仍需脱敏和负载审查。
- 用户可见错误提示由 API/业务的国际化机制决定，技术内部异常消息英文优先；不要把日志语言强制应用于国际化响应。
- Metrics 标签必须低基数；禁止把完整 endpoint、动态业务 ID、MessageId、Secret 直接作为 Metric Tag。

## 5. 禁止/受限 API（见 config/java-api-rules.json）

| 分类 | 规则 | 理由 |
|---|---|---|
| H0 禁止（生产 Runtime） | `System.exit`、`Runtime.exec`、`ProcessBuilder`、`printStackTrace`、`System.out/err` | 破坏宿主/日志治理/命令执行风险 |
| H0 禁止（Transport EventLoop） | `Thread.sleep`、`TimeUnit.sleep`、`CompletableFuture.join` 等同步阻塞 | 事件循环停顿和尾延迟 |
| H0 禁止（可被静态证明的日志） | 参数拼接、字符串首参含中文的运行日志 | 结构漂移/国际化边界 |
| H1 架构审查 | 无界队列/线程池、热路径服务扫描/远程 IO、同步 `whenComplete` 回调、动态反射 | 背压、分配和状态风险 |
| H1 异常审查 | `catch(Throwable)`、忽略中断、空 catch、泛化重试、隐藏失败返回值 | 故障不可见、资源泄漏 |
| H2 风格建议 | 无意义缩写、巨型“Manager/Utils”、机械 Javadoc | 可读性差 |

不能把 `Future.join` 在**所有** Java 线程绝对禁止：启动阶段、worker、测试和 Benchmark 可能合理使用；也不能把 `synchronized`、`ThreadLocal`、反射或 Stream 一刀切，先查线程上下文与热点证据。

### 例外

所有自动禁用规则需明确扫描范围。纯测试/CLI/Benchmark 可有特殊许可；生产代码的例外必须先给出可复现需要、风险缓解、替代方案、owner 与追踪 Issue，并通过 Code Review，不允许单纯放 `@SuppressWarnings` 或修改 Lint 脚本跳过。

### PR-5：新增自动检查边界

`B001` 强制 Core 生产源码不能引入 Vert.x、Nacos、Etcd、Fory、Spring 的实现依赖；不影响 Adapter 模块自身的正常引用。

`R002/R003` 禁止在生产运行时代码中直接创建无容量参数的 `LinkedBlockingQueue` 或 `Executors.newCachedThreadPool`。固定容量、VirtualThread 配合独立 Admission 的方案不应被错误拦截。

`L003` 禁止在常见 SLF4J 调用首参使用 `String.format()` 进行立即格式化；`L004` 将潜在 Token、Secret、Password 日志列为人工审查告警，而**不是**声明已经实现敏感字段数据流追踪。

`N001` 将新增 `IConnectionFactory` 一类 I 前缀接口标为审查项。**对于历史已公开的接口，不得直接因该规则更名**；若需改名必须提供迁移、二进制兼容和 SPI 检查。

上述检查由 `config/java-api-rules.json` 和对应正反向 fixture 管理，历史审计仅生成报告；改变代码前应区分真实风险与误报。

## 6. 并发、性能与安全

- EventLoop 不调用阻塞 IO，不执行用户业务回调；同步 `CompletableFuture` 结束操作可能执行注册回调，必须核对上下文。
- Semaphore/Lease、队列、Retry Budget、分片 Buffer、inflight-byte budget 必须在成功、异常、取消、拒绝、关闭路径一并核查。
- Frame/Decoder 不接受未预算的任意大 payload 或无限对象图；TLS 安全失败不能回退 PLAINTEXT；Registry 断链必须可恢复。
- 只对幂等可重试操作应用显式预算内 Retry；不能用无限循环、无时限连接/重连替代治理。
- 性能优化必须先有真实热点，再用 JMH/JFR/allocation、端到端 p99/p99.9 和固定硬件证据评估，不能将 Shared CI smoke 解释为正式业绩。

## 7. 检查和存量治理

```bash
python3 scripts/check_project.py
python3 scripts/check_java_conventions.py --audit --report target/java-conventions-audit.json
python3 scripts/check_java_conventions.py --changed --base <base-commit-sha>
mvn -B -ntp clean verify -Pquality
```

基础 Lint 只能识别特定语法，无法证明完整 AST、敏感数据数据流或 JVM 并发正确性。PR 阻断仅针对高置信度规则；全仓 `--audit` 统计包括存量违规作为整改计划，未完成的历史差异需要透明报告。纯注释修复和有行为变化的重构应分 PR，标注兼容性和可回滚性。
