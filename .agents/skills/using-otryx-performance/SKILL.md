---
name: using-otryx-performance
description: "OTRYX RPC performance and reliability engineering for Vert.x event-loop, async completion, backpressure, bounded admission, buffer ownership, memory allocation, JMH/JFR profiling and 10k concurrency. Use for transport, scheduling, codec hot paths or claimed throughput/p99 improvements."
---

# Performance & Reliability Gate

1. Inspect transport/consumer/provider call graph and existing benchmark harness. Record actual JDK, runtime flags, payload, codec, concurrency, warmup and measurement conditions.
2. Tag each execution context: Vert.x EventLoop, bounded worker, virtual thread, startup/control plane, test. Never move arbitrary blocking decode or business completion callbacks onto EventLoop. Check `CompletableFuture.whenComplete` synchronous completion behavior.
3. Bound connections, inflight requests/bytes, write queue, CPU queue, retries and pending work. For each rejected/cancelled/timeout path verify idempotent cleanup and ownership of buffers/leases.
4. Keep hot paths free of SPI scanning, Nacos/Etcd IO, dynamic config parsing, blocking waits, unexpected allocations and INFO logs.
5. Preserve Wire v1 and the independent `byte[]` boundary until buffer ownership is proved. Never claim zero-copy from a single local benchmark.
6. Run targeted correctness tests, TLS/fragmentation/rollback/chaos where relevant, JMH `-prof gc`, JFR/allocation stack attribution and an end-to-end cross-JVM scenario.
7. Distinguish GitHub shared-runner smoke from controlled runner evidence: release capacity claims require fixed hardware, repeated AB/BA, p99/p99.9, QPS/core, GC/CPU and a long soak. Report unmeasured fields as unmeasured.

**Fail the review** for EventLoop blocking, unbounded ownership, leaked permit on cancellation, or unsupported statistical claims; performance increase is not a reason to weaken failure semantics.
