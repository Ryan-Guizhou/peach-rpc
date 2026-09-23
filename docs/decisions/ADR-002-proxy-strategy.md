# ADR-002：动态代理是兼容路径，不是最终高性能路径

**状态：已接受**

v0.1 默认使用 JDK Proxy，因为服务契约以接口为主且无额外依赖。CGLIB 作为可选兼容实现。长期默认高性能路径是编译期生成 Client Stub 与 Server Dispatcher。
