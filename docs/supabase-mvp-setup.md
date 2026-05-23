# BioMemo Supabase MVP Setup

This is the backend foundation for the MVP loop:

```text
sign in -> capture/upload photo -> create draft BioRecord -> identify -> confirm -> enrich -> save
```

## Create The Supabase Project

1. Create a new Supabase project in the Supabase dashboard.
2. Copy the project URL and anon key into `local.properties`:

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=YOUR_SUPABASE_ANON_KEY
GOOGLE_WEB_CLIENT_ID=
```

Do not commit real values.

`prodDebug` is the default Android Studio dev variant and points at the actual Supabase
backend. `prodDebug` and `prodRelease` use `SUPABASE_PROD_URL` and
`SUPABASE_PROD_ANON_KEY` when present, then fall back to `SUPABASE_URL` and
`SUPABASE_ANON_KEY`. Use `localDebug` only when you explicitly want `SUPABASE_DEV_URL`
and `SUPABASE_DEV_ANON_KEY`.

BioRecord image identification runs in the Supabase Edge Function, not in the Android app.
Configure the OpenAI key as a Supabase secret:

```bash
supabase secrets set OPENAI_API_KEY=YOUR_OPENAI_API_KEY
```

Optionally override the model:

```bash
supabase secrets set OPENAI_IDENTIFICATION_MODEL=gpt-4.1-mini
```

## Enable Google OAuth

In Supabase Authentication > Providers > Google, enable Google and add the Google Cloud
web client ID and client secret.

In Supabase Authentication > URL Configuration, add the Android callback redirect URL:

```text
biomemo://auth-callback
```

BioMemo's Android manifest and Supabase Kotlin client are configured to use that deeplink
after Google finishes in the browser.

If Google sign-in lands on `localhost:3000`, Supabase is falling back to the project Site URL.
Confirm `biomemo://auth-callback` is present in the additional redirect URLs allow-list, not
only in the Site URL field.

## Apply The Migration

Run the SQL in:

```text
supabase/migrations/20260506000100_auth_and_biorecord_foundation.sql
```

The migration creates:

- `profiles`
- `bio_records`
- `image_metadata`
- `identification_candidates`
- `species_profiles`
- `source_attributions`
- private Storage bucket: `biorecord-photos`

It also enables RLS and owner-only policies for user data.

## Storage Path Convention

Upload BioRecord photos under the authenticated user's ID:

```text
biorecord-photos/{user_id}/{bio_record_id}/original.jpg
biorecord-photos/{user_id}/{bio_record_id}/thumbnail.jpg
```

Storage policies depend on the first path segment matching `auth.uid()`.

## MVP Backend Boundaries

Android client can safely handle:

- image compression
- EXIF extraction
- image dimensions
- MIME type
- thumbnail preview
- fallback location prompts

Backend should own:

- auth and session enforcement
- RLS and ownership checks
- canonical BioRecord storage
- AI identification calls
- enrichment calls
- source attribution
- sensitive coordinate generalization

## Next Android Step

Add the Supabase Kotlin dependencies and a small auth/data client layer, then replace `UserRepository` with Supabase email/password auth.
