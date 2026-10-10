# OTRYX RPC Roadmap

> 1.0.0 GA 之后的方向。以下内容不是承诺日期，只有进入实现并通过相应 Gate 后才会成为 Current。

## 1.0.x

目标：稳定维护。

- Bug fix；
- Security fix；
- 文档/示例改进；
- 不破坏兼容的 Observability 和运维增强；
- 不改变 Wire v1 / Stable Type ID / Schema Fingerprint v1。

## 1.1.x 候选

### 跨语言与 IDL

- Protobuf/IDL；
- 多语言 SDK 的契约生成；
- 字段级演进策略。

### Registry 生态

在不污染 Core 的前提下评估：

- Kubernetes EndpointSlice；
- ZooKeeper；
- Consul。

### 数据面

- Buffer ownership 优化；
- 更低 allocation 的 Pending/Completion 路径；
- Compression 策略；
- 只有 Benchmark Evidence 证明收益后才进入默认路径。

### Streaming

Streaming RPC 是独立设计主题，需要先完成：

- 流控；
- 背压；
- 取消；
- half-close；
- 长连接资源配额；
- 与 Unary 的兼容边界。

## 不做的事情

- 为“功能数量”破坏模块边界；
- 为基准数字牺牲正确性、可维护性和故障边界；
- 未经兼容设计直接复用 Wire/Codec/Type ID；
- 把未经固定环境验证的数据写成生产性能承诺。
