# Bousygram — Coding Agent Workflow

This directory contains **implementation prompts**, not completed code changes. Use them only after the product/architecture documents are approved.

**Required reading order**:
1. [APP_GOAL.md](../../APP_GOAL.md)
2. [ROADMAP.md](../../ROADMAP.md) and [PHASE_PLAN.md](../../PHASE_PLAN.md)
3. [Target architecture](../product/ARCHITECTURE_V2.md) and [ADRs](../adr/)
4. [Master system prompt](MASTER_PROMPT.md)
5. [Phase prompt chain](PHASE_PROMPTS.md): execute ONE selected phase at a time.
6. [Per-commit and per-phase review protocol](REVIEW_PROTOCOL.md) and [QA matrix](QA_MATRIX.md).

## Operating model

- Run **one phase per PR**, on a branch based on the latest verified main, with small reviewable commits.
- **Review every commit** and save evidence, not just a final prose claim.
- Never continue automatically to a subsequent phase; stop and request phase acceptance.
- Always inspect existing code and prior experiments rather than replacing implemented features with generated scaffold.
- A red build blocks merge. Emulator and host unit tests are not substitutes for physical TV and authenticated Telegram verification.
- Preserve core streaming/TDLib/security contracts; Extras remains a maintenance page until separately commissioned.

Use [PR #41](https://github.com/Vaydelux/Bousygram/pull/41) as the initial recovery blocker: its current focus changes fail Kotlin compilation and emulator build; a successful TDLib API generation job is insufficient.

## Artifacts produced by each development phase

- Scoped implementation PR and reviewed commits
- Tests, metrics and real output of checks
- Acceptance checklist / known limitation report
- Updated ADR only when a real architectural decision changes
- Explicit status PASS, BLOCKED or NEEDS_DEVICE_QA
