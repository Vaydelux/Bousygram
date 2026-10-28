# GitHub Actions Audit

Audited checkout HEAD: `e173a68462983c00bb34a371c474c753dd826ddb`.

## Existing remote evidence

The referenced run [`40711655913`](https://github.com/Vaydelux/Bousygram/actions/runs/40711655913) ran workflow `android-enterprise.yml` on commit `a53784eed482b88e035c892f7243a959a7386d88` and failed in `build` with exit code 126. The first failure was the non-executable tracked `gradlew` wrapper; dependent signing and Firebase jobs were skipped. The run also reported Node 20 action deprecations and no diagnostic artifacts.

## Findings

| Area | Finding | Correction |
|---|---|---|
| Wrapper | `gradlew` was tracked as mode `100644`. | Restored mode `100755` and added an early mode/integrity check. |
| SDK | Workflow relied on runner-installed SDK state. | Explicitly installs platform 37, platform-tools, and pinned build-tools. |
| Gradle | Required checks were not independently bounded or clearly staged. | Uses one fail-closed `--console=plain` verification invocation. |
| TDLib | Existing compatibility gate rejects `TdApi.java=cb863c…` versus native `4c6377…`. | Gate remains mandatory before assembly; release remains blocked until regenerated bindings are committed. |
| Debug APKs | Firebase searched for `*debug-universal.apk`, which ordinary debug assembly does not guarantee. | Normalizes exactly one discovered app/test APK using validated directory contents. |
| Firebase | Hardcoded device IDs and optional credentials were ambiguous. | Configuration is explicit; devices are queried before execution; absent configuration is reported NOT CONFIGURED. |
| Signing | Signing was conditionally embedded in ordinary CI. | Moved production signing and publishing to protected release environments. |
| Publishing | No separate controlled release workflow existed. | Added manual, tag/commit-validated draft-then-publish workflow. |

## Workflow inventory

- `android-enterprise.yml`: pull requests, protected branch pushes, and manual verification.
- `android-release.yml`: manual controlled release only; no ordinary branch push trigger.

Action references are pinned to full commit SHAs. Actionlint 1.7.12 passes both workflow files.
