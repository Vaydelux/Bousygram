# Workflow Security

- Workflow token permissions default to read-only.
- Release write permissions are limited to draft creation and final publication jobs.
- Production signing uses the protected `production-signing` environment.
- Keystores are restored only in the signing job, chmodded to owner-only access, and removed in an `always()` cleanup step.
- No ordinary pull-request job receives signing or Telegram data.
- Action dependencies are pinned to full commit SHAs.
- Release publication refuses to overwrite an existing release and requires a protected environment approval.
- Missing device qualification evidence blocks release execution.
