create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    requested_username text;
begin
    requested_username := public.normalize_username(new.raw_user_meta_data ->> 'username');

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

create or replace function public.set_current_profile_username(p_username text)
returns public.profiles
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    clean_username text;
    updated_profile public.profiles%rowtype;
begin
    clean_username := public.normalize_username(p_username);

    if clean_username is null
        or clean_username <> lower(trim(coalesce(p_username, '')))
        or length(clean_username) < 3
        or length(clean_username) > 24 then
        raise exception 'invalid_username' using errcode = '22023';
    end if;

    if exists (
        select 1
        from public.profiles
        where lower(username) = clean_username
            and id <> auth.uid()
    ) then
        raise exception 'username_already_taken' using errcode = '23505';
    end if;

    insert into public.profiles (id, email, username)
    select id, lower(email), clean_username
    from auth.users
    where id = auth.uid()
    on conflict (id) do update
    set
        email = coalesce(public.profiles.email, excluded.email),
        username = excluded.username
    returning * into updated_profile;

    if updated_profile.id is null then
        raise exception 'auth_user_not_found' using errcode = '28000';
    end if;

    return updated_profile;
end;
$$;

revoke all on function public.set_current_profile_username(text) from public;
grant execute on function public.set_current_profile_username(text) to authenticated;
