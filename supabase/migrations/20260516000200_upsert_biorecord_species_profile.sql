create or replace function public.upsert_biorecord_species_profile(
    p_bio_record_id uuid,
    p_common_name text,
    p_scientific_name text,
    p_taxonomy text,
    p_habitat text,
    p_diet text,
    p_lifespan text,
    p_distribution text,
    p_conservation_status text,
    p_source_api text
)
returns public.species_profiles
language plpgsql
security definer
set search_path = public
as $$
declare
    v_profile public.species_profiles%rowtype;
    v_scientific_name text := nullif(trim(p_scientific_name), '');
    v_common_name text := coalesce(nullif(trim(p_common_name), ''), v_scientific_name);
    v_conservation_status text := lower(nullif(trim(p_conservation_status), ''));
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    if v_scientific_name is null then
        raise exception 'scientific name is required';
    end if;

    if not exists (
        select 1
        from public.bio_records
        where id = p_bio_record_id
        and user_id = auth.uid()
    ) then
        raise exception 'BioRecord not found';
    end if;

    if v_conservation_status not in (
        'least concern',
        'near threatened',
        'vulnerable',
        'endangered',
        'critically endangered',
        'extinct in the wild',
        'extinct'
    ) then
        v_conservation_status := null;
    end if;

    insert into public.species_profiles (
        common_name,
        scientific_name,
        taxonomy,
        habitat,
        diet,
        lifespan,
        distribution,
        conservation_status,
        source_api,
        last_enriched_at
    )
    values (
        v_common_name,
        v_scientific_name,
        nullif(trim(p_taxonomy), ''),
        nullif(trim(p_habitat), ''),
        nullif(trim(p_diet), ''),
        nullif(trim(p_lifespan), ''),
        nullif(trim(p_distribution), ''),
        v_conservation_status,
        nullif(trim(p_source_api), ''),
        now()
    )
    on conflict (scientific_name) do update
    set
        common_name = coalesce(excluded.common_name, species_profiles.common_name),
        taxonomy = coalesce(excluded.taxonomy, species_profiles.taxonomy),
        habitat = coalesce(excluded.habitat, species_profiles.habitat),
        diet = coalesce(excluded.diet, species_profiles.diet),
        lifespan = coalesce(excluded.lifespan, species_profiles.lifespan),
        distribution = coalesce(excluded.distribution, species_profiles.distribution),
        conservation_status = coalesce(excluded.conservation_status, species_profiles.conservation_status),
        source_api = coalesce(excluded.source_api, species_profiles.source_api),
        last_enriched_at = now()
    returning * into v_profile;

    update public.bio_records
    set species_profile_id = v_profile.id
    where id = p_bio_record_id
    and user_id = auth.uid();

    return v_profile;
end;
$$;

grant execute on function public.upsert_biorecord_species_profile(
    uuid,
    text,
    text,
    text,
    text,
    text,
    text,
    text,
    text,
    text
) to authenticated;
