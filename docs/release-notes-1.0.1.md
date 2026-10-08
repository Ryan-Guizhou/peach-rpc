# Peach RPC 1.0.1 Release Notes

> 状态：**Release Prep**  
> 版本：`1.0.1`  
> Wire：`v1`  
> Java：`21`  
> Spring Boot：`3.5.4`

1.0.1 是 1.0.0 GA 之后的兼容性 Patch Release。该版本不扩展 Wire 能力，不改变 Stable Type ID / Schema Fingerprint v1 / Registry compatibility metadata，而是集中修复 Provider 异步执行边界、降低健康流量热路径开销，并补齐 Maven Central Patch Release 工程。

## 1. Added

### Maven Central 发布工程

新增 Central Publisher Portal 发布链路：

- `central-release` Maven Profile；
- Maven GPG signing；
- Sonatype Central Publishing Maven Plugin；
- Central publication preflight；
- main/source/javadoc artifact shape 校验；
- Git Tag / Maven Central GAV 不可变保护；
- Validate-only 与 Auto Publish 两种模式；
- Auto Publish 后使用全新 Maven local repository 验证 Starter 能从公开 Central 解析。

Examples、Example API/Provider/Consumer 与 Benchmarks 不进入 Maven Central 发布集合。

### 发布门禁

Release Readiness 改为只验证当前 1.0.x Patch 源码，不再使用最新源码重新构建历史 `1.0.0-RC1` 或 `1.0.0` 版本。

## 2. Changed

### Provider 异步完成模型

业务方法返回 `CompletionStage` 时：

1. Provider 不再通过 `join()` 阻塞 CPU/Virtual Thread worker；
2. 未完成 Stage 后续完成时，响应编码重新调度到 Provider 管理的执行资源；
3. Trace / Metadata Scope 在异步完成阶段重新建立；
4. admission permit 保持到异步业务与响应编码真正完成；
5. 对外完成响应 Future 之前先释放 admission；
6. 有界 CPU completion queue 饱和时 fail-fast 返回 `OVERLOADED`，不会退回 Future completion thread 或 EventLoop 执行编码。

这避免大型响应序列化占用 Vert.x/Netty EventLoop 或其他业务 Future completion thread，同时保持 Provider 并发上限语义。

## 3. Fixed

- 修复异步业务 Stage 可能在外部 completion thread 上执行响应编码的问题。
- 修复响应 Future 已完成但 admission permit 尚未归还导致的瞬时假性 `OVERLOADED`。
- 保持异步取消传播与 admission 生命周期一致。
- 补充 CPU 单 worker、admission 生命周期、外部 completion thread、completion queue 饱和与恢复等回归测试。

## 4. Performance

### 健康路径原子操作

- Circuit Breaker 在 CLOSED/健康成功路径避免重复原子写。
- Retry Budget 已达到额度上限时直接只读返回，不再执行写回相同值的 CAS。
- `EndpointStats.available()` 健康稳态不再每次调用 `System.nanoTime()`。
- ejection 过期后通过 CAS 清零，避免覆盖并发产生的新 ejection。
- Endpoint 成功完成时，连续失败计数已经为 0 则不再重复原子写。

对应 JMH/benchmark harness 已覆盖：

- Circuit CLOSED success；
- saturated Retry Budget；
- healthy Endpoint availability；
- healthy Endpoint success accounting。

## 5. Compatibility

1.0.1 保持 1.0.x 已冻结边界：

- Wire Protocol v1；
- Public Core API；
- Stable Type ID；
- Schema Fingerprint v1；
- Registry compatibility metadata；
- 已分配 Codec / Message Type ID。

1.0.0 与 1.0.1 继续按既有 N/N+1 mixed deployment 与 rollback 流程验证。

## 6. Security

本 Patch 没有新增认证协议、加密协议或 Wire security capability。

TLS / mTLS、Hostname Verification、证书校验与 Reload 行为保持不变。

Maven Central 发布凭据采用 GitHub Repository Secrets 注入：

- `CENTRAL_USERNAME`；
- `CENTRAL_PASSWORD`；
- `MAVEN_GPG_PRIVATE_KEY`；
- `MAVEN_GPG_PASSPHRASE`。

Token、私钥与 passphrase 不写入仓库或 Release Bundle。

## 7. Operational Notes

### Maven Central namespace

当前 Maven groupId 为：

```text
io.peach.rpc
```

第一次公开发布前必须在 Central Publisher Portal 完成 namespace ownership 验证。

若维护者不能证明对 `peach.rpc` DNS namespace 的控制权，则需要在首次 Central 发布前重新确定 groupId。因为 groupId 属于公开依赖坐标，这个决定不能由 CI 自动完成，也不能在发布后无成本修改。

### 纯依赖 Starter

`peach-rpc-spring-boot-starter` 是依赖聚合 Starter，本身不包含实现类。为满足 Central 对 JAR 的 source/javadoc classifier 要求，Release Profile 为该模块生成 placeholder `-sources.jar` 与 `-javadoc.jar`，不会引入无业务意义的 public marker type。

## 8. Upgrade

从 1.0.0 升级到 1.0.1：

1. 保持 Java 21；
2. 保持 Spring Boot 3.5.4 基线；
3. 将 Peach RPC 依赖版本更新为 `1.0.1`；
4. 不需要修改 Wire 配置；
5. 不需要修改 Stable Type ID；
6. 不需要修改 Schema Fingerprint；
7. 不需要修改 Registry compatibility metadata。

Spring Boot Starter：

```xml
<dependency>
    <groupId>io.peach.rpc</groupId>
    <artifactId>peach-rpc-spring-boot-starter</artifactId>
    <version>1.0.1</version>
</dependency>
```

正式 Maven Central 发布完成前，可以从源码执行：

```bash
mvn -B -ntp clean install -DskipTests
```

## 9. Rollback

若 1.0.1 上线后发现问题，可按既有 1.0.x 兼容策略回滚到 1.0.0：

1. 停止继续扩大 1.0.1 流量；
2. 将 Provider/Consumer 按滚动方式回退到 1.0.0；
3. 不覆盖 `v1.0.1` Git Tag 或 Maven Central GAV；
4. 修复后发布新的 Patch 版本。

运行时流程见 [升级与回滚](upgrade-rollback.md)。

## 10. Verification

1.0.1 release-prep 必须通过：

- CI；
- Release Readiness；
- Rolling Compatibility；
- Maven reactor + Javadoc doclint；
- 10k logical-concurrency soak harness；
- performance smoke evidence；
- Independent JVM Examples；
- Maven Central publication preflight；
- main/source/javadoc artifact shape validation。

## 11. Performance Evidence Boundary

1.0.1 包含可测量的热路径优化，但不会因此声明统一的生产 QPS、p99/p99.9、QPS/Core 或“比其他框架快 X%”。

正式性能/容量结论仍必须来自固定硬件、受控参数和可重复 Evidence。

## 12. Known Limitations

- 当前公开 RPC 模型仍以 Unary RPC 为主；
- Streaming 尚未进入 1.0.x；
- Protobuf/IDL 与跨语言 SDK 尚未进入 1.0.x；
- Maven Central 的真实公开发布仍取决于 namespace ownership、Portal Token 与 GPG signing key；
- 没有固定环境 Evidence 时，不提供统一生产容量数字。
