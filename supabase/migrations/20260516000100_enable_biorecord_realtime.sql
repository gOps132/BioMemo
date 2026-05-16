do $$
begin
    if exists (
        select 1 from pg_publication
        where pubname = 'supabase_realtime'
    ) then
        if not exists (
            select 1 from pg_publication_tables
            where pubname = 'supabase_realtime'
            and schemaname = 'public'
            and tablename = 'bio_records'
        ) then
            alter publication supabase_realtime add table public.bio_records;
        end if;

        if not exists (
            select 1 from pg_publication_tables
            where pubname = 'supabase_realtime'
            and schemaname = 'public'
            and tablename = 'species_profiles'
        ) then
            alter publication supabase_realtime add table public.species_profiles;
        end if;
    end if;
end $$;
