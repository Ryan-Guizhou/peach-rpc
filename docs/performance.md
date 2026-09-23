# 性能基准与优化规则

Peach RPC 不接受没有可重复环境信息的“高性能”结论。

性能验证至少分为：JMH 微基准、Raw Transport 基准、端到端 RPC、故障/过载基准。需要记录 QPS/Core、p50/p99/p99.9、CPU、Allocation、GC、inflight、连接数和错误率。

热路径规则：

1. 请求不访问 Registry。
2. 请求不执行 SPI 扫描。
3. TCP 长连接复用并限制 inflight。
4. Service ID 与 Method ID 在 `refer()`/Provider 注册阶段预计算。
5. 用户代码不运行在 Event Loop。
6. 虚拟线程不是容量策略，Provider 仍必须有并发准入。
7. 后续优先使用生成式 Stub/Dispatcher 替代动态代理和通用分派。
