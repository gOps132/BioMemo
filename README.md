<h1 align="center">BioMemo</h1>

<p align="center">
  <strong>A mobile field journal for capturing organisms, identifying species, and building searchable biodiversity records.</strong>
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-SDK%2036-123d2a?style=flat-square&logo=android">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2-58a56a?style=flat-square&logo=kotlin">
  <img alt="Supabase" src="https://img.shields.io/badge/Supabase-Auth%20%2B%20Postgres-123d2a?style=flat-square&logo=supabase">
  <img alt="OpenAI" src="https://img.shields.io/badge/OpenAI-Image%20ID-58a56a?style=flat-square&logo=openai">
  <img alt="Gradle" src="https://img.shields.io/badge/Gradle-9.1-123d2a?style=flat-square&logo=gradle">
  <img alt="CI" src="https://img.shields.io/badge/GitHub%20Actions-ready-58a56a?style=flat-square&logo=githubactions">
</p>

<p align="center">
  <a href="#product">Product</a> ·
  <a href="#features">Features</a> ·
  <a href="#local-development">Local Development</a> ·
  <a href="#supabase-setup">Supabase Setup</a>
</p>

<p align="center">
  <img alt="BioMemo logo" src="app/src/main/res/drawable/biomemo_icon_noborder.png" width="180">
</p>

## Product

BioMemo is an Android biodiversity notebook for field observations. It helps users capture or upload organism photos, preserve useful metadata, identify likely species, and review records through collection, detail, search, and map views.

Current experience focuses on a reliable BioRecord workflow: users can create observations from camera or gallery, crop photos, store location and field notes, retry image identification when needed, inspect species details, and view records on a map with photo-first markers.

## Features

- Email and Google auth backed by Supabase.
- Required username setup for Google sign-up before entering the app.
- Camera and gallery capture flow with crop preview and photo compression.
- BioRecord persistence with image metadata, notes, GPS, and saved dates.
- Supabase Edge Function identification powered by OpenAI image analysis.
- Retry flow for failed or missing organism identification.
- Species search and enrichment via Supabase Edge Functions.
- Collection, detail, dashboard, search, profile, and map screens.
- Realtime-aware record store so activities refresh when BioRecords change.
- Vertical slices for auth, records, and species domain/data code.
- GitHub Actions for Android unit tests, Android build, and Supabase database tests.

## Stack

| Layer | Technology |
| --- | --- |
| Mobile app | Android SDK 36, Kotlin 2.2, AppCompat, Material Components |
| Architecture | MVP screens, vertical feature slices, repository/use-case boundary |
| Backend | Supabase Auth, Postgres, Storage, Realtime, Edge Functions |
| AI services | OpenAI image identification through Supabase Edge Functions |
| Map | osmdroid |
| Local services | Supabase CLI, local.properties |
| CI | GitHub Actions, Gradle, Supabase DB tests |

## Repository Layout

```txt
.
├── app/                       Android application module
│   ├── src/main/java/...      MVP screens, navigation, UI helpers, feature slices
│   ├── src/main/res/          Layouts, drawables, themes, BioMemo assets
│   └── src/test/              Unit tests for presenters, repositories, stores, models
├── docs/                      Supabase setup and project notes
├── supabase/                  Migrations, seed data, database tests, Edge Functions
├── gradle/                    Version catalog and wrapper files
├── local.properties.example   Local Android/Supabase configuration template
└── .github/                   CI workflows and pull request template
```

## Local Development

Copy local configuration:

```bash
cp local.properties.example local.properties
```

Fill required Android values:

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=YOUR_SUPABASE_ANON_KEY
GOOGLE_WEB_CLIENT_ID=YOUR_GOOGLE_WEB_CLIENT_ID
```

Default Android Studio dev variant is `prodDebug`. It points at the real Supabase backend. Use `localDebug` only when testing against local Supabase values:

```properties
SUPABASE_DEV_URL=http://10.0.2.2:54321
SUPABASE_DEV_ANON_KEY=YOUR_LOCAL_ANON_KEY
```

Run Android unit tests:

```bash
./gradlew :app:testProdDebugUnitTest
```

Build a debug APK:

```bash
./gradlew :app:assembleProdDebug
```

Run all unit tests across variants:

```bash
./gradlew test
```

## Supabase Setup

Start local Supabase when working on migrations, policies, and database tests:

```bash
supabase start
supabase db reset
supabase test db
```

Configure Edge Function secrets before running image identification or species enrichment in a deployed Supabase project:

```bash
supabase secrets set OPENAI_API_KEY=YOUR_OPENAI_API_KEY
```

Deploy Edge Functions:

```bash
supabase functions deploy identify-biorecord-image
supabase functions deploy species-search
supabase functions deploy species-enrichment-preview
```

## CI

Pull requests run:

- Android unit tests with `./gradlew :app:testProdDebugUnitTest`.
- Android debug build with `./gradlew :app:assembleDebug`.
- Supabase database tests with `supabase test db`.

## Documentation

More project notes live in `docs/` and `supabase/`:

- `docs/supabase-mvp-setup.md`
- `supabase/migrations/`
- `supabase/functions/`
- `supabase/tests/database/`
