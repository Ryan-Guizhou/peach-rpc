---
name: using-otryx-compatibility
description: "OTRYX 2.0 API migration and legacy Peach RPC 1.0.x compatibility review for Wire v1, Schema Fingerprint, codec identifiers, stable type IDs, exported public APIs, SPI providers and Spring configuration. Use before modifying protocol, serialization, registry metadata, generated stubs, starters or any public contract."
---

# Compatibility Gate

**Approved exception:** the 2026-10-10 OTRYX 2.0 rename may break the Java API/GAV at a new major version. It does **not** authorize Wire v1, message/codec IDs, or frozen registry metadata changes. Historical 1.0.x compatibility claims remain scoped to the old version; migration must explicitly disclose cross-generation incompatibility.

1. Inspect the exact affected source and `docs/wire-compatibility.md`, `docs/protocol.md`, `docs/spi.md` and tests; verify 1.0.x freeze from the current branch.
2. Classify impact by contract:
   - Wire: 32-byte header, message/code IDs, frame limits, handshake and status semantics.
   - Codec/Schema: stable type IDs, Object[] representation, fingerprint v1 and DTO evolution.
   - Public Java API: constructors, method descriptors, exported interfaces and runtime exceptions.
   - Registry: compatibility metadata key, discovery and registration semantics.
   - Starter: ConfigurationProperties key, activation defaults, auto-configuration order and SPI discovery.
3. For each affected contract, state backward/forward compatibility and N/N+1 plus rollback behavior. Do not treat successful compilation as binary/wire compatibility.
4. For breaking proposals, stop and request an explicit versioning/migration decision; never silently update IDs, names or schema under a style-only PR.
5. Add precise positive and negative tests, rolling compatibility and independent JVM examples where applicable. Keep Chinese/English documentation in sync.
6. Report impact ledger with contract, callers, risk, migration plan, and tests. If any compatibility axis is untested, disclose it instead of claiming success.

**Outside the explicitly approved 2.0 migration, do not automatically rename** public Record components, serialized types, `@OtryxRpcService` members, SPI names or Maven artifact coordinates.
