# Codex Dev-Test

This repository is wired into the local multi-model dev-test loop through `codex-dev-test`.

- Default checks: backend Maven tests, frontend Vitest, and frontend build.
- Deployment and localhut smoke checks are opt-in with `--acceptance`.
- Test-agent writes are limited to `ai_artifacts/` and `.codex/dev-test/runs/`.
- `avoid_slop_research.md`, `env.txt`, and `logs/` are protected local state.

Example:

```bash
codex-dev-test test /home/duwei/aienie-projects/AINovel --dry-run
codex-dev-test test /home/duwei/aienie-projects/AINovel
```
