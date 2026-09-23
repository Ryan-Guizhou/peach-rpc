# 性能基准与优化规则

Peach RPC 不接受没有可重复环境信息的“高性能”结论。

性能验证至少分为：JMH 微基准、Raw Transport 基准、端到端 RPC、故障/过载基准。需要记录 QPS/Core、p50/p99/p99.9、CPU、Allocation、GC、inflight、连接数和错误率。

## 当前热路径规则

1. 请求不访问 Registry。
2. 请求不执行 SPI 扫描。
3. TCP 长连接复用并限制 inflight。
4. Service ID 与 Method ID 在 `refer()` / Provider 注册阶段预计算。
5. `RpcMethodCodec` 在服务方法粒度预绑定，不在单次请求解析 `Method` 或参数类型。
6. Generated Consumer Stub 存在时优先于动态代理。
7. 用户代码不运行在 Event Loop。
8. 虚拟线程不是容量策略，Provider 仍必须有并发准入。
9. 框架错误使用 Core 稳定二进制格式，不依赖业务 Codec。

## V2-A 仍存在的已知热路径成本

V2-A 的 Generated Stub 已消除 Consumer 的 `Method` 动态分派，但方法参数仍通过 `Object[]` 传递，`RpcMethodCodec` 当前仍输出 `byte[]`。因此 V2-A 的目标是稳定 Binding 契约，不宣称已经达到最终零分配路径。

## V2-B 性能主线

下一阶段重点验证并实现：

1. arity-specific Generated CallSite，减少或消除 `Object[]` 分配；
2. Generated Server Dispatcher，替换默认 MethodHandle 通用分派；
3. Buffer-oriented Codec，减少中间 `byte[]` 与内存复制；
4. Event Loop 亲和连接组和 connection-local pending table；
5. Fory 显式类型注册与方法级 encoder/decoder 缓存；
6. Generated / JDK Proxy / Byte Buddy 的 JMH 对比；
7. Raw Vert.x 基线与完整 RPC Added Latency 对比。

所有优化必须通过基准证明收益，不能仅因为“理论上更快”进入默认路径。
