# BioMemo Agent Guide

## Project Shape
- Native Android app using Kotlin activities, XML layouts, and a simple MVP-style presenter layer.
- Keep changes dependency-light until Supabase/auth architecture is intentionally introduced.
- Prefer small feature screens and plain Kotlin data/repository classes over broad rewrites.

## UI Direction
- BioMemo should feel like a polished field journal: forest greens, warm surfaces, rounded cards, clear hierarchy, and practical nature-memory workflows.
- Reuse shared colors and drawable backgrounds in `app/src/main/res` before adding new one-off styling.
- Keep auth UI honest: Google sign-in is scaffolded only until the real Supabase/Google auth phase lands.

## Config And Secrets
- Never commit real API keys or secrets.
- Put local values in `local.properties` and document expected keys in `local.properties.example`.
- Treat `BuildConfig` values as optional unless a feature explicitly requires them.

## Validation
- Run targeted unit tests for new Kotlin behavior.
- Run `./gradlew :app:assembleDebug` before handoff after Android resource or manifest changes.
- Run `supabase test db` after Supabase migration, RLS policy, auth trigger, RPC, or storage policy changes. Start/reset the local Supabase database first when needed.
- Local agent hooks live in `.agents/hooks/` when present:
  - `.agents/hooks/verify-before-handoff.sh` detects changed Android/Supabase files and runs relevant checks.
  - `.agents/hooks/verify-supabase-local.sh` runs `supabase db start`, `supabase db reset`, and `supabase test db`.
  - Add `--stop` to the Supabase hook when the local stack should be stopped after verification.

## Git Workflow
- `main` is protected. Do not push directly to `main`, force-push `main`, bypass branch protection, or merge failing work.
- Use feature branches for code changes, preferably `codex/<short-task-name>`.
- Use pull requests into `main` for shared work. Required checks are `Android Unit Test`, `Android Build`, and `Supabase DB Test`.
- Agent handoff order is: run the local verification hook, review the diff, stage the intended files only, commit, push the feature branch, then open or update the pull request.
- Do not commit before the local pass is green unless the user explicitly accepts the failed or skipped verification and the commit message/PR notes explain it.
- Merging a green PR to `main` triggers the production Supabase migration workflow after the `production` GitHub Environment approval gate.
- Before PR handoff, run relevant local verification for touched areas. For Android resources or manifest changes, include `./gradlew :app:assembleDebug`.
- Do not commit generated planning, critique, audit, prompt, or agent context artifacts unless the user explicitly asks to track them.
- Never expose production Supabase secrets to pull request workflows. Production migration secrets belong only in GitHub Actions secrets and the `production` Environment.
