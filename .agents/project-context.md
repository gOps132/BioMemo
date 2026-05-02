# BioMemo Project Context

## Current Product Phase
BioMemo is preparing core app features before real Supabase auth. The current phase uses local sample data, local auth placeholders, and safe config scaffolding.

## Decisions Locked In
- Native Android XML/Kotlin remains the implementation stack for now.
- Supabase URL, anon key, Google web client ID, and AI identification API key are read from `local.properties` into `BuildConfig`.
- Google sign-in is visible as a disabled/info CTA and must not fake-auth users.
- Core shell includes dashboard, Bio collection, search, capture placeholder, and profile.

## Future Phases
- Implement Supabase email/password auth.
- Implement native Google sign-in and exchange the ID token with Supabase.
- Replace the capture placeholder with camera permissions, image capture, and AI identification flow.
