# Per-Commit Review and Phase Quality Gate

Run this protocol **after every commit** and at **the end of a single phase**. A code change is not “working” because it was pushed.

## Commit review (repeat per SHA)

~~~text
Commit SHA:
Phase / work item:
Files changed:
Why it was necessary:
Actual behavior before / after:
Scope control / unintended changes:
Security/privacy: auth, account cache, stream tokens, permissions, log content:
API/schema/backwards-compatibility implications:
D-pad/focus/touch/TV/tablet/phone implications:
Playback/cache/native ABI/release implications:
Tests executed, command, actual result:
CI run URL and exact commit (if available):
Visual or device evidence (or NOT RUN):
Review findings & corrections:
Revert/rollback plan:
Status: PASS | BLOCKED | NEEDS_DEVICE_QA
~~~

Review commit changes against **its parent SHA**, not only its final PR diff. Check for type errors, duplicate focus nodes, invalid imports, incomplete callbacks, stale state, unauthorized media leaks and navigation fallthrough. Fix violations in a new corrective commit and review that new commit too; do not rewrite already shared history to hide failures.

## End-of-phase report

Write docs/reviews/PHASE-N-REVIEW.md (or equivalent PR attachment) containing:
1. Phase goal, baseline/main SHA and branch HEAD.
2. Requirement-to-implementation traceability; list actual changed files and each commit SHA.
3. Architectural decisions, dependencies and correctness/security risks.
4. Per-screen/input coverage: TV, tablet, phone, D-pad, remote Back, TalkBack, offline/auth/session.
5. Actual commands run and *exact* passed/failed/skipped/not-run results, with CI and instrumentation links.
6. Measured performance before/after where the phase claims an improvement (device/method documented).
7. Open bugs grouped by P0/P1/P2, mitigating steps, rollback and compatibility notes.
8. Decision: **PASS** only if acceptance criteria and required automated gates met; **NEEDS_DEVICE_QA** if hardware verification remains; **BLOCKED** if red build, missing security gate, or correctness defect.
9. Stop: no next-phase implementation until explicit review acceptance.

## Failure rules

- Compilation fails → stop feature development, isolate first error, fix, rerun.
- Emulator red → inspect underlying compile/runner error; do not treat “no emulator assertion executed” as a test failure in UI logic.
- Failing flaky test → reproduce and report, do not silently skip or delete.
- Hardware unavailable → attach manual test instructions and mark NOT RUN; no production readiness claim.
- Auth, device logout/cache purge, JNI, player engine, signing and user files are high-risk paths requiring a focused review and rollback.

## Baseline example — pending PR #41

At 2026-10-31 review, PR #41 had red required checks. Kotlin compiler output on EnterpriseComponents.kt:643 showed assignment of Boolean to a ShellDestination due to a name collision involving selected. It needs a **source fix + a fresh exact-head CI run**. No merger or “TV focus done” declaration until that occurs.
