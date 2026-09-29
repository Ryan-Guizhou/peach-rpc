# Peach RPC Examples

本目录展示推荐的真实部署结构：**契约共享，Provider 与 Consumer 分进程运行**。

## 模块

- `peach-rpc-example-api`：仅包含 RPC Contract、DTO 和编译期生成代码；
- `peach-rpc-example-provider`：只发布 `@PeachRpcService`；
- `peach-rpc-example-consumer`：只注入 `@PeachRpcReference` 并发起调用。

Provider 与 Consumer 都同时产出：

- 普通 JAR：供 Reactor 内测试和其他模块作为依赖使用；
- `*-exec.jar`：Spring Boot 可执行 JAR，供独立进程运行。

## 运行

在仓库根目录执行：

```bash
docker compose -f peach-rpc-examples/docker-compose.yml up -d
mvn -B -ntp -pl peach-rpc-examples -am clean package
java -jar peach-rpc-examples/peach-rpc-example-provider/target/*-exec.jar
```

Provider 启动后，在另一个终端执行：

```bash
java -jar peach-rpc-examples/peach-rpc-example-consumer/target/*-exec.jar
```

Provider 应输出类似：

```text
Peach RPC server started: bind=0.0.0.0:19090, advertised=127.0.0.1:19090
```

Consumer 应输出：

```text
RPC demo completed successfully: Hello, Peach RPC!
```

Consumer 在启动阶段允许等待短暂的注册中心传播时间，但真实业务调用仍遵循 Peach RPC 的超时、重试、熔断和端点剔除规则。


## TLS / mTLS 可选 Profile

默认示例仍使用 PLAINTEXT，保证本地和 CI 的 Nacos 恢复 E2E 不需要证书准备。

Provider/Consumer 额外提供：

- `application-tls.yml`
- `application-mtls.yml`

这些 Profile 只引用环境变量，不在仓库保存证书或私钥。

### TLS

Provider 需要：

```text
PEACH_RPC_TLS_CERT=<provider certificate PEM path>
PEACH_RPC_TLS_KEY=<provider private key PEM path>
```

Consumer 需要：

```text
PEACH_RPC_TLS_CA=<trusted CA PEM path>
```

分别使用 Spring Profile `tls` 启动 Provider 与 Consumer。Consumer 默认启用 hostname verification，因此调用地址必须与 Provider 证书身份匹配。

### mTLS

Provider 和 Consumer 都需要配置：

```text
PEACH_RPC_TLS_CERT=<local certificate PEM path>
PEACH_RPC_TLS_KEY=<local private key PEM path>
PEACH_RPC_TLS_CA=<trusted CA PEM path>
```

分别使用 Spring Profile `mtls` 启动。Provider 会要求合法客户端证书，握手失败不会降级到 plaintext。

证书在线 Reload、有效期校验和故障语义见 [TLS / mTLS 安全指南](../docs/security.md)。

## 可选 Observability Adapter

Examples 默认不强制引入观测框架。需要验证生产观测集成时，可按需给 Provider/Consumer 增加：

- `peach-rpc-observability-micrometer`
- `peach-rpc-observability-opentelemetry`
- `peach-rpc-observability-jfr`

其中 Micrometer/OpenTelemetry 在 Spring Context 存在对应 Bean 时自动装配；JFR 通过 `peach.rpc.observability.jfr.enabled=true` 启用。完整指标、Trace Context 与 JFR 事件见 [可观测性指南](../docs/observability.md)。
