# 从 Peach RPC 1.0.x 迁移到 OTRYX RPC 2.0

> **迁移状态：2.0.0-SNAPSHOT；尚未形成新旧版本直接互通保证。**

## Maven 与 Java API

| 类型 | Peach RPC 1.0.x | OTRYX 2.0 |
|---|---|---|
| Maven groupId | io.peach.rpc | com.peachsoft.otryx |
| Maven artifactId | peach-rpc-* | otryx-* |
| Java namespace | io.peach.rpc.* | com.peachsoft.otryx.* |
| Public type / annotation | PeachRpc* | OtryxRpc* |
| Spring Boot config | peach.rpc.* | otryx.rpc.* |
| Protocol header | Wire v1 | Wire v1 |

## 为什么这次升级是 Breaking Change

旧版的 RpcTypeIds 从 Java 全限定类名计算稳定类型标识，RpcIds 从方法名及参数/返回值类型计算方法标识，RpcSchemaFingerprint 将这些信息组合进 SHA-256。因此，改动包名可能改变 **Type ID、Method ID、Schema Fingerprint**，尽管报文的 Wire v1 Header、Magic 和消息类型没有改变。

**冻结且必须保留的 Registry Metadata Key：**

- peach.rpc.protocol.version
- peach.rpc.schema.version
- peach.rpc.schema.fingerprint

这些是历史协议字段，不属于新项目的 Spring 配置前缀。

## 迁移流程

1. 固定旧版 Provider/Consumer 与业务 DTO 的源码、构建产物和服务注册设置；不要覆盖旧 GAV 或 Tag。
2. 将 pom.xml 中的 io.peach.rpc:peach-rpc-* 修改成 com.peachsoft.otryx:otryx-*。
3. 更新 io.peach.rpc.* import、PeachRpc* 公开类名及注解，清理旧版生成的 Stub。
4. 配置项改为 otryx.rpc.*，对 Nacos/Etcd 的 namespace、group 与 ServiceKey 做单独审查，避免将新旧无法互通的业务契约登记到同一服务集合。
5. 同时构建与部署 OTRYX Provider/Consumer，先做独立进程的正确性、TLS、Chaos 和失败路径测试。
6. 在隔离流量环境测试真实新旧混合版本；未提供通过证据前采用蓝绿或业务级分流，不采用自动滚动混合升级。
7. 回滚时保留旧版注册组、旧依赖和完整配置，防止新消费者错误连接到旧提供者。

## 稳定快照与兼容证据

重构前稳定分支为 stable/peach-rpc-1.0.1-pre-otryx-2026-10-10，基准 SHA 为 a4175635aef8702cab613b9c672ca7edbaae6a94，主 CI 成功。其发布状态为 1.0.1 Release Prep，不应表述为已经正式发布的 1.0.1 GA。

新版本的互通、性能与安全声明以实际 CI 和受控环境 Evidence 为准。

## 发布前条件

- 验证 com.peachsoft.otryx 的 Maven Central namespace 所有权。
- 审核 OTRYX 名称在软件相关类别的既有使用/商标风险。
- GitHub 仓库正式改名后校验所有 SCM、Workflow、Badge 和文档链接。
