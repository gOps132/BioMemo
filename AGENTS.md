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
