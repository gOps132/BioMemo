begin;

select plan(17);

select ok(to_regclass('public.profiles') is not null, 'profiles table exists');
select ok(to_regclass('public.species_profiles') is not null, 'species_profiles table exists');
select ok(to_regclass('public.bio_records') is not null, 'bio_records table exists');
select ok(to_regclass('public.image_metadata') is not null, 'image_metadata table exists');
select ok(to_regclass('public.identification_candidates') is not null, 'identification_candidates table exists');
select ok(to_regclass('public.source_attributions') is not null, 'source_attributions table exists');

select ok(
  exists (select 1 from storage.buckets where id = 'biorecord-photos' and public = false),
  'biorecord-photos bucket exists and is private'
);

select ok((select relrowsecurity from pg_class where oid = 'public.profiles'::regclass), 'profiles has RLS enabled');
select ok((select relrowsecurity from pg_class where oid = 'public.species_profiles'::regclass), 'species_profiles has RLS enabled');
select ok((select relrowsecurity from pg_class where oid = 'public.bio_records'::regclass), 'bio_records has RLS enabled');
select ok((select relrowsecurity from pg_class where oid = 'public.image_metadata'::regclass), 'image_metadata has RLS enabled');
select ok((select relrowsecurity from pg_class where oid = 'public.identification_candidates'::regclass), 'identification_candidates has RLS enabled');
select ok((select relrowsecurity from pg_class where oid = 'public.source_attributions'::regclass), 'source_attributions has RLS enabled');

select ok(to_regprocedure('public.handle_new_user()') is not null, 'handle_new_user function exists');
select ok(to_regprocedure('public.normalize_username(text)') is not null, 'normalize_username function exists');
select ok(to_regprocedure('public.resolve_login_identifier(text)') is not null, 'resolve_login_identifier function exists');
select ok(to_regclass('public.profiles_username_unique') is not null, 'profiles username unique index exists');

select * from finish();

rollback;
