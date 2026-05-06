alter table public.profiles
add column if not exists email text,
add column if not exists username text;

create or replace function public.normalize_username(value text)
returns text
language sql
immutable
as $$
    select nullif(regexp_replace(lower(trim(coalesce(value, ''))), '[^a-z0-9_-]+', '', 'g'), '');
$$;

with profile_candidates as (
    select
        p.id,
        lower(u.email) as auth_email,
        coalesce(
            public.normalize_username(p.field_name),
            public.normalize_username(split_part(u.email, '@', 1)),
            lower(substr(p.id::text, 1, 8))
        ) as base_username,
        row_number() over (
            partition by coalesce(
                public.normalize_username(p.field_name),
                public.normalize_username(split_part(u.email, '@', 1)),
                lower(substr(p.id::text, 1, 8))
            )
            order by p.created_at, p.id
        ) as username_rank
    from public.profiles p
    join auth.users u on u.id = p.id
)
update public.profiles p
set
    email = coalesce(p.email, profile_candidates.auth_email),
    username = coalesce(
        p.username,
        case
            when profile_candidates.username_rank = 1 then profile_candidates.base_username
            else profile_candidates.base_username || '-' || lower(substr(p.id::text, 1, 8))
        end
    )
from profile_candidates
where profile_candidates.id = p.id;

create unique index if not exists profiles_email_unique
on public.profiles (lower(email))
where email is not null;

create unique index if not exists profiles_username_unique
on public.profiles (lower(username))
where username is not null;

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
        public.normalize_username(new.raw_user_meta_data ->> 'field_name'),
        coalesce(public.normalize_username(split_part(new.email, '@', 1)), 'user') || '-' || lower(substr(new.id::text, 1, 8))
    );

    insert into public.profiles (
        id,
        email,
        username,
        field_name,
        first_name,
        last_name,
        avatar_url
    )
    values (
        new.id,
        lower(new.email),
        requested_username,
        coalesce(
            new.raw_user_meta_data ->> 'field_name',
            new.raw_user_meta_data ->> 'username',
            new.raw_user_meta_data ->> 'name'
        ),
        new.raw_user_meta_data ->> 'first_name',
        new.raw_user_meta_data ->> 'last_name',
        new.raw_user_meta_data ->> 'avatar_url'
    )
    on conflict (id) do update
    set
        email = coalesce(public.profiles.email, excluded.email),
        username = coalesce(public.profiles.username, excluded.username),
        field_name = coalesce(public.profiles.field_name, excluded.field_name);

    return new;
end;
$$;

create or replace function public.resolve_login_identifier(identifier text)
returns table(email text)
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    clean_identifier text := lower(trim(identifier));
    clean_username text := public.normalize_username(identifier);
begin
    if clean_identifier is null or clean_identifier = '' then
        return;
    end if;

    return query
    select lower(u.email)::text
    from auth.users u
    left join public.profiles p on p.id = u.id
    where lower(u.email) = clean_identifier
        or lower(coalesce(p.email, '')) = clean_identifier
        or (
            clean_username is not null
            and lower(coalesce(p.username, '')) = clean_username
        )
    limit 1;
end;
$$;

revoke all on function public.resolve_login_identifier(text) from public;
grant execute on function public.resolve_login_identifier(text) to anon, authenticated;
