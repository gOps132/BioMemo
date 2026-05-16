insert into auth.users (
    instance_id,
    id,
    aud,
    role,
    email,
    encrypted_password,
    email_confirmed_at,
    confirmation_sent_at,
    confirmation_token,
    recovery_token,
    email_change_token_new,
    email_change_token_current,
    email_change,
    phone_change,
    phone_change_token,
    reauthentication_token,
    raw_app_meta_data,
    raw_user_meta_data,
    is_sso_user,
    is_anonymous,
    created_at,
    updated_at
)
values (
    '00000000-0000-0000-0000-000000000000',
    '11111111-1111-1111-1111-111111111111',
    'authenticated',
    'authenticated',
    'demo@biomemo.dev',
    crypt('password', gen_salt('bf')),
    now(),
    now(),
    '',
    '',
    '',
    '',
    '',
    '',
    '',
    '',
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"username":"demo"}'::jsonb,
    false,
    false,
    now(),
    now()
)
on conflict (id) do update
set
    email = excluded.email,
    encrypted_password = excluded.encrypted_password,
    email_confirmed_at = excluded.email_confirmed_at,
    confirmation_token = excluded.confirmation_token,
    recovery_token = excluded.recovery_token,
    email_change_token_new = excluded.email_change_token_new,
    email_change_token_current = excluded.email_change_token_current,
    email_change = excluded.email_change,
    phone_change = excluded.phone_change,
    phone_change_token = excluded.phone_change_token,
    reauthentication_token = excluded.reauthentication_token,
    raw_app_meta_data = excluded.raw_app_meta_data,
    raw_user_meta_data = excluded.raw_user_meta_data,
    is_sso_user = excluded.is_sso_user,
    is_anonymous = excluded.is_anonymous,
    updated_at = now();

insert into auth.identities (
    id,
    user_id,
    provider_id,
    identity_data,
    provider,
    last_sign_in_at,
    created_at,
    updated_at
)
values (
    '11111111-1111-1111-1111-111111111111',
    '11111111-1111-1111-1111-111111111111',
    '11111111-1111-1111-1111-111111111111',
    '{"sub":"11111111-1111-1111-1111-111111111111","email":"demo@biomemo.dev","email_verified":true}'::jsonb,
    'email',
    now(),
    now(),
    now()
)
on conflict (provider, provider_id) do update
set
    user_id = excluded.user_id,
    identity_data = excluded.identity_data,
    updated_at = now();

insert into public.profiles (
    id,
    email,
    username
)
values (
    '11111111-1111-1111-1111-111111111111',
    'demo@biomemo.dev',
    'demo'
)
on conflict (id) do update
set
    email = excluded.email,
    username = excluded.username,
    updated_at = now();
