begin;

select plan(7);

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
) values (
  '10000000-0000-0000-0000-000000000001',
  'authenticated',
  'authenticated',
  'trail@biomemo.app',
  crypt('password', gen_salt('bf')),
  now(),
  '{"provider":"email","providers":["email"]}'::jsonb,
  '{"field_name":"Trail Scout","username":"Trail Scout"}'::jsonb,
  now(),
  now()
);

select is(public.normalize_username(' Trail Scout! '), 'trailscout', 'normalize_username strips unsafe characters');

select is(
  (select email from public.profiles where id = '10000000-0000-0000-0000-000000000001'),
  'trail@biomemo.app',
  'auth trigger stores lower-case profile email'
);

select is(
  (select username from public.profiles where id = '10000000-0000-0000-0000-000000000001'),
  'trailscout',
  'auth trigger stores normalized profile username'
);

select is(
  (select field_name from public.profiles where id = '10000000-0000-0000-0000-000000000001'),
  'Trail Scout',
  'auth trigger stores field name'
);

select is(
  (select email from public.resolve_login_identifier('trail@biomemo.app') limit 1),
  'trail@biomemo.app',
  'resolve_login_identifier resolves auth email'
);

select is(
  (select email from public.resolve_login_identifier('trailscout') limit 1),
  'trail@biomemo.app',
  'resolve_login_identifier resolves username'
);

select is(
  (select email from public.resolve_login_identifier('') limit 1),
  null,
  'resolve_login_identifier ignores blank input'
);

select * from finish();

rollback;
