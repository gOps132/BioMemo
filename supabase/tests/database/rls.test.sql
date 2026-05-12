begin;

select plan(10);

insert into auth.users (
  id,
  aud,
  role,
  email,
  encrypted_password,
  email_confirmed_at,
  raw_app_meta_data,
  raw_user_meta_data,
  created_at,
  updated_at
) values
(
  '20000000-0000-0000-0000-000000000001',
  'authenticated',
  'authenticated',
  'alpha@biomemo.app',
  crypt('password', gen_salt('bf')),
  now(),
  '{"provider":"email","providers":["email"]}'::jsonb,
  '{"field_name":"Alpha Scout","username":"alpha"}'::jsonb,
  now(),
  now()
),
(
  '20000000-0000-0000-0000-000000000002',
  'authenticated',
  'authenticated',
  'beta@biomemo.app',
  crypt('password', gen_salt('bf')),
  now(),
  '{"provider":"email","providers":["email"]}'::jsonb,
  '{"field_name":"Beta Scout","username":"beta"}'::jsonb,
  now(),
  now()
);

insert into public.bio_records (
  id,
  user_id,
  photo_url,
  source_type,
  location_label
) values (
  '30000000-0000-0000-0000-000000000002',
  '20000000-0000-0000-0000-000000000002',
  'biorecord-photos/20000000-0000-0000-0000-000000000002/beta/original.jpg',
  'upload',
  'Beta grove'
);

select set_config('request.jwt.claim.sub', '20000000-0000-0000-0000-000000000001', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;

insert into public.bio_records (
  id,
  user_id,
  photo_url,
  source_type,
  location_label
) values (
  '30000000-0000-0000-0000-000000000001',
  '20000000-0000-0000-0000-000000000001',
  'biorecord-photos/20000000-0000-0000-0000-000000000001/alpha/original.jpg',
  'upload',
  'Alpha grove'
);

select is((select count(*)::integer from public.bio_records), 1, 'user sees only own BioRecords');

select is(
  (select location_label from public.bio_records where id = '30000000-0000-0000-0000-000000000001'),
  'Alpha grove',
  'user can read own BioRecord'
);

select is(
  (select location_label from public.bio_records where id = '30000000-0000-0000-0000-000000000002'),
  null,
  'user cannot read another user BioRecord'
);

select throws_ok(
  $$insert into public.bio_records (user_id, source_type, location_label) values ('20000000-0000-0000-0000-000000000002', 'upload', 'Blocked grove')$$,
  '42501',
  'new row violates row-level security policy for table "bio_records"',
  'user cannot insert BioRecord for another user'
);

insert into public.image_metadata (
  bio_record_id,
  file_type,
  width,
  height
) values (
  '30000000-0000-0000-0000-000000000001',
  'image/jpeg',
  1024,
  768
);

select is((select count(*)::integer from public.image_metadata), 1, 'user can read own image metadata');

select throws_ok(
  $$insert into public.image_metadata (bio_record_id, file_type) values ('30000000-0000-0000-0000-000000000002', 'image/jpeg')$$,
  '42501',
  'new row violates row-level security policy for table "image_metadata"',
  'user cannot insert image metadata for another user BioRecord'
);

insert into storage.objects (
  bucket_id,
  name
) values (
  'biorecord-photos',
  '20000000-0000-0000-0000-000000000001/30000000-0000-0000-0000-000000000001/original.jpg'
);

select is((select count(*)::integer from storage.objects), 1, 'user can read own BioRecord photo object');

select throws_ok(
  $$insert into storage.objects (bucket_id, name) values ('biorecord-photos', '20000000-0000-0000-0000-000000000002/blocked/original.jpg')$$,
  '42501',
  'new row violates row-level security policy for table "objects"',
  'user cannot upload into another user photo path'
);

reset role;

select set_config('request.jwt.claim.sub', '20000000-0000-0000-0000-000000000002', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;

select is((select count(*)::integer from public.bio_records), 1, 'second user sees only own BioRecords');
select is((select count(*)::integer from storage.objects), 0, 'second user cannot read first user photo object');

reset role;

select * from finish();

rollback;
