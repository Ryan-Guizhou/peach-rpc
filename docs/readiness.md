# 生产就绪门禁

当前版本定位为 Preview，可用于内部验证和中型项目集成试点，但不直接宣称生产就绪。

正式成为中型项目默认 RPC 层之前，至少需要完成：

- 协议兼容与畸形帧测试；
- Etcd 集成、Watch 恢复和 compaction 测试；
- Cancel、Retry Budget、熔断与异常实例剔除；
- Provider graceful drain/GO_AWAY；
- TLS/mTLS；
- Micrometer、Tracing、JFR；
- 端到端稳定性能基线；
- 容量规划与升级/回滚文档。

当前已经具备的基础约束包括长连接、多路复用、有界 inflight、Provider 并发准入、本地服务目录、SPI 边界、协议长度校验和显式生命周期。
