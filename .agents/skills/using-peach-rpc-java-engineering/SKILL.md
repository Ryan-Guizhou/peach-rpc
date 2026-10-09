---
name: using-peach-rpc-java-engineering
description: "Peach RPC Java code authoring and review: apply naming conventions, package boundaries, standard Chinese Javadoc, English structured logs, exception handling and resource lifecycle rules. Use when creating, editing or reviewing Java source, Spring Boot configuration, or API documentation."
---

# Java Engineering Workflow

1. Read root `AGENTS.md`, `docs/engineering/java-coding-standard.md`, current source, POM and tests. Do not infer implemented behavior from a template.
2. Inventory exported and internal symbols before renaming. Treat `io.peach.rpc.api`, public Core/SPI, serialization fields, service method IDs and Spring property keys as compatibility boundaries.
3. Prefer precise English names: `ServiceKey`, `resolveEndpoint`, `maxInflightBytes`. Express units in identifier; do not append `Utils`, `Manager`, `Helper` without a real abstraction.
4. Keep Core independent of adapters; keep one coherent responsibility per component. Favor constructor injection, immutable snapshots where useful, no new unbounded queues.
5. Write Chinese Javadoc with genuine contracts: inputs/null, output/ownership, errors, concurrency, cancellation, cleanup and compatibility when relevant. Use standard tags. Add `@since` **only if source history verifies introduction version**. Never copy Peach Cloud `@Author/@Version/@CreateTime`.
6. Write English SLF4J parameterized messages for real events. Keep identifiers consistent; do not log full request/response, Metadata, token, secret, signed URL or private key. Avoid INFO in per-RPC hot paths.
7. Avoid catch-and-ignore, `printStackTrace`, blocking EventLoop calls and unnecessary public signatures. Do not blindly replace APIs that are legal in tests or worker threads.
8. Run affected unit tests, `python3 scripts/check_project.py`, Maven `-Pquality`, and `scripts/check_java_conventions.py` when present. Report actual failures and exceptions.

**Example — precise contract**:

```java
/**
 * 获取指定服务的不可变端点快照。
 *
 * <p>结果不包含远端注册中心实时查询；调用方不能修改返回集合。</p>
 *
 * @param key 已规范化的服务键，不允许为 null
 * @return 当前可用端点集合，未发现时返回空集合
 */
List<RpcEndpoint> endpoints(ServiceKey key);
```

Do not claim the example method exists in this project.
