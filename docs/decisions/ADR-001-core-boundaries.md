# ADR-001：第三方基础设施不得进入 Core

**状态：已接受**

Core 仅依赖 JDK、SLF4J 和 Peach RPC 自身契约。Vert.x、Jetcd、Fory、CGLIB、Spring 等依赖位于 Adapter 或 Starter 模块。

这样可以降低传递依赖压力，保证替换能力，并避免第三方生命周期类型定义 RPC 编程模型。
