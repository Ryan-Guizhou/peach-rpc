# Peach RPC Examples

该模块是 Peach RPC 的可运行 Spring Boot 示例，同时也是 Reactor 中的启动烟测。

## 覆盖能力

示例会在同一个 Spring Boot 进程中启动：

```text
GreetingRunner
    |
    | @PeachRpcReference
    v
Generated Consumer Stub
    |
    v
PeachRpcClient
    |
    v
Vert.x TCP
    |
    v
PeachRpcServer
    |
    v
Generated Provider Dispatcher
    |
    v
GreetingServiceImpl
```

默认使用 Memory Registry，因此不依赖外部 Etcd，可直接运行。

`GreetingServiceImpl` 只需要 `@PeachRpcService`，该注解本身已经是 Spring stereotype，不需要额外添加 `@Component`。

## 直接运行

在仓库根目录执行：

```bash
mvn -B -ntp -pl peach-rpc-examples -am clean package
java -jar peach-rpc-examples/target/peach-rpc-examples-0.1.0-SNAPSHOT.jar
```

启动成功后日志应包含类似：

```text
Peach RPC server started at 127.0.0.1:19090
RPC demo completed successfully: Hello, Peach RPC!
```

也可以使用：

```bash
mvn -B -ntp -pl peach-rpc-examples -am spring-boot:run
```

## 配置说明

默认配置位于 `src/main/resources/application.yml`：

- Registry：Memory；
- Provider：127.0.0.1:19090；
- Consumer timeout：3s；
- 每 Endpoint 连接数：2；
- Provider drain timeout：5s；
- Provider control-plane timeout：3s；
- DIRECT execution 默认关闭；
- 幂等调用最大 attempt：2。

切换到 Etcd 时，将 Registry 改为：

```yaml
peach:
  rpc:
    registry:
      type: etcd
      endpoints: http://127.0.0.1:2379
      namespace: example
```

## 自动化验证

`ExampleApplicationSmokeTest` 不只是验证 Spring Context。

测试会：

1. 为 Provider 选择临时空闲端口；
2. 启动完整 Spring Boot Context；
3. 扫描并注册 `@PeachRpcService`；
4. 注入 `@PeachRpcReference`；
5. 使用 Generated Consumer Stub；
6. 经 Vert.x TCP + Fory 发起真实 Unary RPC；
7. 断言结果为 `Hello, Peach RPC!`；
8. 关闭 Context 并验证 Provider 正常排空。

执行：

```bash
mvn -B -ntp -pl peach-rpc-examples -am test
```

整个项目的最终门禁仍是：

```bash
python3 scripts/check_project.py
mvn -B -ntp clean verify -Pquality
```
