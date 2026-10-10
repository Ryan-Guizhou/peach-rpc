---
name: review-otryx-changes
description: "Evidence-grounded OTRYX RPC pull request code review. Use when reviewing Java source changes, Agent-generated code, CI regressions, naming/Javadoc/log rules, forbidden APIs, current-version contract correctness, performance or security before submitting or merging a PR."
---

# Diff Review Procedure

1. Get exact branch/base/head SHA and diff, not just a PR summary. Inspect changed call sites, tests, dependency changes, affected docs and CI from the **same SHA**.
2. Run the repository checks, `python3 scripts/check_java_conventions.py --changed --base <base-sha>` (where available) and targeted Maven tests. A missing tool, network or CI run is **not** passing evidence.
3. Check in order:
   - P0 security/secrets, current Wire/Codec/Schema contract correctness or unintended runtime API changes, EventLoop blocking, unbounded memory or resource leak.
   - P1 lifecycle/race/exception swallowing, incorrect retry/cancellation, log sensitivity and public Javadoc contract drift.
   - P2 naming/complexity/duplication, style/format and missing regression tests.
   - Evidence: shared-runner smoke vs controlled p99 and allocation measurements, PR diff scope, README Chinese/English parity.
4. For the pre-GA V1.1 governance programme, do not require old unpublished API/Wire compatibility. Inspect Chinese @Author/@Version/@CreateTime provenance, staged Javadoc metadata audit and English logging without secret/Throwable leakage.
5. Output actionable findings with severity, path:line, demonstrated failure mechanism, smallest safe fix and validation needed. Do not invent source lines or hide uncertain concerns as confirmed bugs.
6. Stop if a blocking issue is unresolved; advise Draft and request tests. Do not merge, publish, force-push, delete branches or alter permissions without explicit current user authorization.
7. If review is clean, report scope of inspected files, checks performed, residual risks and evidence; never assert universal correctness merely from a green CI.
