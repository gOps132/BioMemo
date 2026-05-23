## Summary

-

## Change Type

- [ ] Feature
- [ ] Bug fix
- [ ] Refactor
- [ ] Documentation
- [ ] CI / deployment
- [ ] Security

## Quality Review

- [ ] Scope is focused and unrelated churn is avoided
- [ ] Code follows existing project patterns
- [ ] User-facing behavior is described clearly
- [ ] Edge cases and failure paths were considered
- [ ] No secrets, credentials, or local-only artifacts are committed

## Tests and Verification

- [ ] Android unit tests pass or are not affected: `./gradlew :app:testProdDebugUnitTest`
- [ ] Android debug build passes or is not affected: `./gradlew :app:assembleDebug`
- [ ] Supabase DB tests pass or are not affected: `supabase test db`
- [ ] New/changed behavior has tests, or the test gap is explained
- [ ] Database/schema changes are backward-compatible or migration impact is described

## Supabase Deployment Risk

- [ ] No Supabase migration change
- [ ] No Supabase Edge Function change
- [ ] Migration is safe to apply on production after merge to `main`
- [ ] Edge Function changes are safe to deploy after merge to `main`
- [ ] Destructive migration has rollback or mitigation notes
- [ ] Dashboard schema edits, if any, were pulled back into migrations

## UI Review

- [ ] No visible UI change
- [ ] Relevant screens were checked after UI changes
- [ ] Text, spacing, contrast, and responsive layout were reviewed from rendered output

## Notes

-
