---
name: using-otryx-java-engineering
description: "OTRYX RPC Java engineering baseline: Chinese Peach Cloud type Javadoc, English SLF4J logs, naming, resource lifecycle and staged quality gates."
---

# OTRYX Java Engineering (V1.1)

1. Read AGENTS.md, docs/engineering/java-coding-standard.md and the affected code, tests, POM and configuration. Code and tests override historical documentation.
2. For each hand-authored top-level Java class/interface/record/enum/annotation, provide genuine Chinese Javadoc and exactly one type-level @Author, @Version and @CreateTime. Use yyyy/M/d HH:mm. Do not fabricate past creation times, authorship or versions; when unverifiable, surface the audit exception instead of inventing metadata.
3. Explain meaningful API and SPI contracts: parameters and nullability, returns and ownership, exceptions, thread/callback context, blocking, timeouts, cancellation, cleanup, compatibility within the current release and state transitions. Avoid tautological comments.
4. Retain SLF4J LoggerFactory (no Lombok introduction solely for logs); English parameterized messages, stable reason codes and safe diagnostic identifiers. INFO is for lifecycle; WARN for recoverable abnormal conditions; ERROR for actionable failures. Review log/trace/exporter sink sensitivity and avoid INFO for each RPC.
5. Never dump request/response, token, passwords, keys, private metadata, signed URLs or untrusted Throwable messages. A trailing Throwable is permissible only after a sensitivity review; otherwise log safe error type and correlation ID.
6. Use clear domain English identifiers, unit suffixes (Millis/Nanos/Bytes), 4 spaces, UTF-8/LF, no wildcard imports, 120-column Java lines. Keep Core independent of adapters and use constructor injection for Spring components.
7. Do not block EventLoop, swallow interrupts, create unbounded queues, or change Future completion or resource ownership under a style-only PR. New semantics and structural behavior changes require separate review and tests.
8. Project is pre-GA: no backward-compatibility requirement for unreleased versions, but current Wire/Codec/Registry/Starter correctness and security remain mandatory.
9. Verify with Python metadata fixtures, --changed and --audit reports, existing Checkstyle, Maven -Pquality and targeted tests. Full --enforce-all is the PR-07 exit gate, not a false PR-02 compliance claim.

## Type Javadoc template

~~~java
/**
 * 定义当前服务发现能力的边界。
 *
 * <p>说明线程归属、返回快照及资源释放责任。</p>
 *
 * @Author Mr Shu
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/10/10 09:30
 */
~~~

Template metadata is illustrative, never a substitute for verifying the actual type history.
