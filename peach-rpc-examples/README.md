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
