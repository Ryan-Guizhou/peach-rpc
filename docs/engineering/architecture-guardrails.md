# OTRYX RPC：架构边界的自动化验证

> **状态：PR-7 提议并实施的规则；是否通过以对应 PR Head 的 Maven/CI 为准。** 规则不引入任何公开 Java API、Wire v1 或 Codec/Schema 变更。

## 1. 目标与实际模块

OTRYX RPC 的 `otryx-core` 同时承载 Public API、协议、SPI 抽象与轻量级 Runtime。Vert.x、Nacos、Etcd、Fory、Spring 属于独立 Adapter/Starter 的具体实现；让这些依赖反向进入 Core 会导致 Core 体积、生命周期耦合与升级成本上升。

~~~mermaid
flowchart LR
    App["应用 / examples"] --> Starter["Spring Boot Starter"]
    Starter --> Core["otryx-core: API + SPI + Runtime"]
    Transport["Vert.x Transport"] --> Core
    Registry["Nacos / Etcd Registry"] --> Core
    Codec["Fory Codec"] --> Core
    Observability["Micrometer / OpenTelemetry / JFR"] --> Core
    Core --> SLF4J["SLF4J"]
~~~

上图箭头表示**可依赖方向**；Core 不应反向导入 Vert.x、注册中心、序列化或 Spring 的实现。独立 SPI、Codec、Registry 适配器可以依赖 Core 暴露的中立契约。

## 2. 机器门禁

使用仅在 `otryx-core` 测试作用域存在的 ArchUnit `1.5.1`，通过 Java 21 编译产生的字节码审查：

- **CORE-01**：`com.peachsoft.otryx..` 的生产类不得直接依赖 `io.vertx..`、`com.alibaba.nacos..`、`io.etcd..`、`org.apache.fory..`、`org.springframework..` 或仓库中 Adapter 实现包。
- **CORE-02**：公共 `com.peachsoft.otryx.api..` 不得直接依赖 `com.peachsoft.otryx.core..` 运行时实现。
- 生产 Java 类由 `ClassFileImporter` 导入，并排除测试目录，因此 JUnit、测试实现或基准工具不受这两个规则限制。
- 现有的源码级 `B001` 作为快速预警和 changed-file 门禁；ArchUnit 检查**编译后的类型依赖**，是更可靠的结构验证，但不能证明反射字符串、动态类加载、MCP 配置或运行时资源安全。

### 执行

从仓库根目录执行：

~~~bash
mvn -B -ntp -pl otryx-core -am -Dtest=OtryxRpcCoreArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -ntp clean verify -Pquality
python3 scripts/test_java_conventions.py
~~~

ArchUnit 不应加载或启动任何 Nacos/Etcd 服务端，测试只检查已编译字节码。

## 3. 与接口兼容约束的关系

架构依赖正确并不等于 Wire 兼容、Java 二进制兼容和业务语义正确。进行接口重命名、Record 字段变更、Stable Type ID、Codec、Schema Fingerprint 修改时仍需按当前 `using-otryx-compatibility` 进行契约正确性审查、Consumer/Provider 互操作与针对性协议测试；首发前不要求历史开发版本兼容。

本规则不强制大规模 package 调整，也不强制“所有接口独立成另一个 Maven 模块”；只有发现真实依赖反向渗透时才需修正。必要例外须有设计说明、兼容性证据、独立审批，禁止直接删除 ArchUnit 规则使 CI 转绿。

## 4. 扩展方向（尚未实现）

完整依赖分析可能进一步包括：Maven 模块依赖环、跨 Adapter 的依赖隔离、Spring Starter 自动装配顺序和 SPI 一致性检查。这些规则需要先对当前依赖图做可复现审计，避免未经证据引入大量误报。
