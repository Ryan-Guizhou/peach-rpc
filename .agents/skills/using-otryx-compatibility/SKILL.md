---
name: using-otryx-compatibility
description: "Review current OTRYX RPC Wire, Codec, Schema, Registry, SPI and Spring contracts for correctness, safe evolution and interoperability before first GA."
---

# First-Release Contract Correctness Gate (V1.1)

OTRYX RPC 1.0.0-SNAPSHOT is not yet GA. No Java, SPI, configuration or wire backward-compatibility requirement applies to historical unpublished Peach RPC or OTRYX snapshots. This does not waive the need for verifiable **current-version correctness**.

1. Inspect the concrete source, POM, tests, docs/protocol.md, docs/spi.md and relevant configuration before proposing a public or wire change.
2. Classify effects on Frame/Handshake/Codec/Schema/Type IDs, generated stubs, Registry metadata, SPI discovery, Starter properties, error statuses and consumer/provider interoperation.
3. For breaking redesigns, document the new single-release contract, affected call sites, tests, rollout within current examples and the smallest revert path; do not retain deprecated compatibility bridges solely for unpublished versions.
4. Verify encoding/decoding round trips, length and allocation budgets, malformed frames, duplicate IDs, wire negotiation, cancellation, timeouts, security and independent consumer/provider JVM operation as appropriate.
5. Keep structural/Javadoc/logging PRs behavior-neutral. A change to protocol, error handling, concurrency, serializers or public behavior belongs in a separate implementation PR with evidence.
6. Historical rolling-compatibility tests are optional archival evidence; current-version interop and contract correctness tests remain required. Do not confuse one with the other.

Record open questions and evidence gaps; never infer correctness from compilation alone.
