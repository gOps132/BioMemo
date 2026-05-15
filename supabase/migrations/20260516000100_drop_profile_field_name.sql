update public.profiles
set username = coalesce(
    public.normalize_username(username),
    public.normalize_username(field_name),
    public.normalize_username(split_part(email, '@', 1)),
    'user-' || lower(substr(id::text, 1, 8))
)
where username is null
    and to_regclass('public.profiles') is not null;

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    requested_username text;
begin
    requested_username := coalesce(
        public.normalize_username(new.raw_user_meta_data ->> 'username'),
        public.normalize_username(new.raw_user_meta_data ->> 'name'),
        coalesce(public.normalize_username(split_part(new.email, '@', 1)), 'user') || '-' || lower(substr(new.id::text, 1, 8))
    );

    insert into public.profiles (
        id,
        email,
        username,
        first_name,
        last_name,
        avatar_url
    )
    values (
        new.id,
        lower(new.email),
        requested_username,
        new.raw_user_meta_data ->> 'first_name',
        new.raw_user_meta_data ->> 'last_name',
        new.raw_user_meta_data ->> 'avatar_url'
    )
    on conflict (id) do update
    set
        email = coalesce(public.profiles.email, excluded.email),
        username = coalesce(public.profiles.username, excluded.username);

    return new;
end;
$$;

alter table public.profiles
drop column if exists field_name;
