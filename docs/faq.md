# OTRYX RPC FAQ

## 1. 1.0.x 是否继续冻结 Wire？

是。1.0.x 使用 Wire v1。兼容边界见 [Wire Compatibility](wire-compatibility.md)。

## 2. 是否支持 gRPC / HTTP/2？

1.0 不支持。当前 Transport 是 OTRYX RPC 自定义 Wire v1 over Vert.x TCP。

## 3. 是否支持 Protobuf？

1.0 默认 Codec 是 Fory，没有内置 Protobuf IDL。

## 4. 是否支持 Streaming？

1.0 仅 Unary RPC。

## 5. 为什么默认是 Virtual Thread？

多数企业 Java RPC Provider 最终会调用 JDBC、文件或同步 SDK。JDK 21 Virtual Thread 能简化阻塞业务接入，但框架仍用 admission 限制最大并发。

## 6. CPU 密集任务怎么办？

使用 `@OtryxRpcExecution(RpcExecutionMode.CPU)`，进入有界平台线程池。

## 7. 为什么 DIRECT 默认关闭？

DIRECT 会在 Transport Event Loop 执行业务代码。任何阻塞或长计算都会破坏网络事件处理，因此必须显式开启。

## 8. Retry 为什么必须标记幂等？

框架无法替业务判断“重复执行是否安全”。只有 `@OtryxRpcIdempotent` 方法才允许自动 Retry。

## 9. Registry 故障会不会立刻导致所有调用失败？

短时控制面故障不会主动清空 last-known-good 本地目录。已有数据面连接仍可以继续工作；恢复后 Adapter 会重新注册/订阅并收敛。

## 10. 为什么 Schema Fingerprint 很严格？

1.0 优先保证升级行为确定。DTO/方法结构变化会改变 Fingerprint；需要并行升级时应发布新的 `ServiceKey.version`。

## 11. 旧 Consumer 能调用新 Provider 吗？

同一 Wire v1、同一业务契约下可以。Rolling Compatibility Workflow 会验证 N/N+1 和 rollback 矩阵。

## 12. 支持 TLS/mTLS 吗？

支持。见 [安全指南](security.md)。

## 13. 有哪些观测方式？

Micrometer、OpenTelemetry 和 JFR。见 [可观测性](observability.md)。

## 14. 有官方 QPS 数字吗？

没有把 shared CI Runner 数字当作官方生产性能承诺。项目提供 JMH/Soak/Evidence 工具，使用者应在自己的固定硬件环境生成容量数据。

## 15. 如何从源码构建当前 1.0.0-SNAPSHOT？

```bash
mvn -B -ntp clean install
```

## 16. 如何验证历史 RC1？

历史 RC1 的版本、包名与公开 API 属于 Peach RPC 1.x 基线；必须在其实际历史源码或对应标签上验证，不应在当前 OTRYX 1.0 的 main 上通过修改 Maven revision 冒充旧版构建。参见[历史发布记录](archive/releases/release-notes-1.0.0-RC1.md)与[迁移限制](migration.md)。

## 17. 如何提交问题？

普通 Bug/Feature 使用 GitHub Issue；安全漏洞不要公开披露，按根目录 [SECURITY.md](../SECURITY.md) 操作。

## 18. 如何贡献？

阅读 [CONTRIBUTING.md](../CONTRIBUTING.md) 和 [开发指南](engineering/development.md)，所有 PR 必须通过自动化门禁。
