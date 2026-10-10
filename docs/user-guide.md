# OTRYX RPC 用户指南

本页提供最短可验证集成路径。运行命令来自仓库中的 Maven Reactor 与官方独立 JVM Examples。

## 环境与版本

- JDK **21**、Maven、Git。
- Spring Boot **3.5.4**（Spring 集成示例）。
- 项目源码与 Maven 坐标均为 **`2.0.0-SNAPSHOT`**。当前不能从 Maven Central 直接下载安装，先本地构建。

## 先运行真实 RPC

在仓库根目录：

```bash
mvn -B -ntp clean install -DskipTests
mvn -B -ntp -pl otryx-examples -am clean package
```

Provider 和 Consumer 请分别在两个终端启动，使用仓库的 Nacos 示例环境和官方配置：

```bash
# Provider terminal
java -jar otryx-examples/otryx-example-provider/target/*-exec.jar
```

```bash
# Consumer terminal
java -jar otryx-examples/otryx-example-consumer/target/*-exec.jar
```

**重要**：独立进程示例默认依赖 Nacos 服务与对应配置。请在启动前先按 [快速开始](getting-started.md) 和 [Examples README](../otryx-examples/README.md) 完成 Nacos 启动、确认端口与启动顺序。也可以直接通过 `bash scripts/run_example_process_e2e.sh` 运行项目现有的独立进程自动化验收（需满足脚本运行环境）。

## 在已有 Spring Boot 项目中引用

推荐先使用轻量 Starter，完整 Starter 的扩展与替代关系见 [Starter 细节](reference/starter.md)。

```xml
<dependency>
    <groupId>com.peachsoft.otryx</groupId>
    <artifactId>otryx-spring-boot-starter-lite</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

示例内存 Registry 配置（单节点本地验证；不是跨进程注册中心）：

```yaml
otryx:
  rpc:
    enabled: true
    registry:
      type: memory
```

业务接口声明、Provider 导出、Consumer 注入、编译期 Stub 与 Nacos/TLS 的实际示例和约束请使用 [Starter](reference/starter.md) 和 [入门教程](getting-started.md) 的源码片段，不要将伪代码替代为可运行实现。

## 重要运行边界

- 本项目目前优先支持 **Unary RPC**，不声明 Streaming RPC 能力。
- Retry 只能用于业务明示幂等的请求；超时、Admission 和队列上限应随真实负载测试。
- PLAINTEXT 仅用于受信实验环境，跨信任边界须配置 TLS/mTLS。
- Wire v1 保留不代表 Peach RPC 1.x 与 OTRYX 2.0 互通，尤其是 Java 类名变化造成的 Type ID、Method ID 和 Schema Fingerprint 变更。

继续阅读：[配置](configuration.md) · [安全](security.md) · [兼容迁移](migration.md) · [FAQ](faq.md)。
