# Peach RPC 1.0.0-RC1 Release Notes

> 类型：Release Candidate  
> 目标：冻结 1.0.x 核心契约并完成 GA 前最终稳定性验证。

## 1. 核心冻结

1. Wire Protocol v1；
2. Public Core API；
3. Stable Type ID；
4. Schema Fingerprint v1；
5. Registry Compatibility Metadata；
6. Codec/Message Type 已分配编号。

## 2. 主要能力

- Unary RPC；
- Compile-time Codegen + fallback；
- Vert.x TCP/TLS/mTLS；
- Fory Codec；
- Memory/Etcd/Nacos Registry；
- P2C + EWMA；
- Timeout / Retry Budget / Circuit Breaker / Outlier Ejection；
- Provider Virtual Thread / CPU / guarded DIRECT；
- Graceful Drain / CANCEL / GO_AWAY；
- Micrometer / OpenTelemetry / JFR；
- N/N+1 rolling compatibility 与 rollback；
- Benchmark / 10k soak / Chaos / Release Readiness。

## 3. RC1 验证门禁

RC1 要求以下全部通过：

- CI；
- Release Readiness；
- Rolling Compatibility；
- Etcd Chaos；
- Nacos Chaos；
- Independent JVM Examples。

## 4. 已知限制

- 仅 Unary RPC；
- 当前无跨语言 IDL/SDK；
- 当前无 Streaming；
- Fory Native 主要面向 Java；
- Compression 数据面只允许 NONE；
- 没有固定硬件 Evidence 时不发布官方生产 QPS/SLO/容量数字。

## 5. 从 RC1 到 GA

GA 期间只接受：

- Blocker/Major bug fix；
- 文档和示例修正；
- 安全修复；
- 不破坏 RC1 冻结契约的发布工程改进。
