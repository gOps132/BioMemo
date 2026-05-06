create extension if not exists pgcrypto;

create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    field_name text,
    first_name text,
    middle_name text,
    last_name text,
    avatar_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.species_profiles (
    id uuid primary key default gen_random_uuid(),
    common_name text not null,
    scientific_name text not null unique,
    taxonomy text,
    habitat text,
    diet text,
    lifespan text,
    distribution text,
    conservation_status text check (
        conservation_status is null or conservation_status in (
            'least concern',
            'near threatened',
            'vulnerable',
            'endangered',
            'critically endangered',
            'extinct in the wild',
            'extinct'
        )
    ),
    source_api text,
    last_enriched_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.bio_records (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles(id) on delete cascade,
    species_profile_id uuid references public.species_profiles(id) on delete set null,
    photo_url text,
    thumbnail_url text,
    source_type text not null check (source_type in ('camera', 'upload', 'sample')),
    observed_at timestamptz,
    saved_at timestamptz not null default now(),
    latitude double precision,
    longitude double precision,
    location_label text not null default 'location unknown',
    notes text,
    confidence_score integer check (confidence_score is null or confidence_score between 0 and 100),
    verification_status text not null default 'draft' check (
        verification_status in ('draft', 'analyzing', 'needs confirmation', 'verified', 'failed')
    ),
    metadata_availability text not null default 'unknown',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.image_metadata (
    id uuid primary key default gen_random_uuid(),
    bio_record_id uuid not null unique references public.bio_records(id) on delete cascade,
    captured_at timestamptz,
    latitude double precision,
    longitude double precision,
    orientation integer,
    file_type text,
    width integer,
    height integer,
    metadata_raw jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create table public.identification_candidates (
    id uuid primary key default gen_random_uuid(),
    bio_record_id uuid not null references public.bio_records(id) on delete cascade,
    common_name text,
    scientific_name text not null,
    confidence_score integer check (confidence_score is null or confidence_score between 0 and 100),
    reasoning text,
    visible_traits text,
    uncertainty_notes text,
    selected boolean not null default false,
    created_at timestamptz not null default now()
);

create table public.source_attributions (
    id uuid primary key default gen_random_uuid(),
    species_profile_id uuid not null references public.species_profiles(id) on delete cascade,
    source_name text not null,
    source_url text,
    field_name text not null,
    retrieved_at timestamptz not null default now()
);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

create trigger species_profiles_set_updated_at
before update on public.species_profiles
for each row execute function public.set_updated_at();

create trigger bio_records_set_updated_at
before update on public.bio_records
for each row execute function public.set_updated_at();

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles (id, field_name, first_name, last_name, avatar_url)
    values (
        new.id,
        coalesce(new.raw_user_meta_data ->> 'field_name', new.raw_user_meta_data ->> 'name'),
        new.raw_user_meta_data ->> 'first_name',
        new.raw_user_meta_data ->> 'last_name',
        new.raw_user_meta_data ->> 'avatar_url'
    )
    on conflict (id) do nothing;

    return new;
end;
$$;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

alter table public.profiles enable row level security;
alter table public.species_profiles enable row level security;
alter table public.bio_records enable row level security;
alter table public.image_metadata enable row level security;
alter table public.identification_candidates enable row level security;
alter table public.source_attributions enable row level security;

create policy "Profiles are readable by owner"
on public.profiles for select
to authenticated
using (auth.uid() = id);

create policy "Profiles are editable by owner"
on public.profiles for update
to authenticated
using (auth.uid() = id)
with check (auth.uid() = id);

create policy "Species profiles are readable by signed in users"
on public.species_profiles for select
to authenticated
using (true);

create policy "BioRecords are readable by owner"
on public.bio_records for select
to authenticated
using (auth.uid() = user_id);

create policy "BioRecords are insertable by owner"
on public.bio_records for insert
to authenticated
with check (auth.uid() = user_id);

create policy "BioRecords are editable by owner"
on public.bio_records for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

create policy "BioRecords are deletable by owner"
on public.bio_records for delete
to authenticated
using (auth.uid() = user_id);

create policy "Image metadata is readable by BioRecord owner"
on public.image_metadata for select
to authenticated
using (
    exists (
        select 1 from public.bio_records
        where bio_records.id = image_metadata.bio_record_id
        and bio_records.user_id = auth.uid()
    )
);

create policy "Image metadata is insertable by BioRecord owner"
on public.image_metadata for insert
to authenticated
with check (
    exists (
        select 1 from public.bio_records
        where bio_records.id = image_metadata.bio_record_id
        and bio_records.user_id = auth.uid()
    )
);

create policy "Identification candidates are readable by BioRecord owner"
on public.identification_candidates for select
to authenticated
using (
    exists (
        select 1 from public.bio_records
        where bio_records.id = identification_candidates.bio_record_id
        and bio_records.user_id = auth.uid()
    )
);

create policy "Identification candidates are insertable by BioRecord owner"
on public.identification_candidates for insert
to authenticated
with check (
    exists (
        select 1 from public.bio_records
        where bio_records.id = identification_candidates.bio_record_id
        and bio_records.user_id = auth.uid()
    )
);

create policy "Identification candidates are editable by BioRecord owner"
on public.identification_candidates for update
to authenticated
using (
    exists (
        select 1 from public.bio_records
        where bio_records.id = identification_candidates.bio_record_id
        and bio_records.user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.bio_records
        where bio_records.id = identification_candidates.bio_record_id
        and bio_records.user_id = auth.uid()
    )
);

create policy "Source attributions are readable by signed in users"
on public.source_attributions for select
to authenticated
using (true);

insert into storage.buckets (id, name, public)
values ('biorecord-photos', 'biorecord-photos', false)
on conflict (id) do nothing;

create policy "Users can read their own BioRecord photos"
on storage.objects for select
to authenticated
using (
    bucket_id = 'biorecord-photos'
    and split_part(name, '/', 1) = auth.uid()::text
);

create policy "Users can upload their own BioRecord photos"
on storage.objects for insert
to authenticated
with check (
    bucket_id = 'biorecord-photos'
    and split_part(name, '/', 1) = auth.uid()::text
);

create policy "Users can update their own BioRecord photos"
on storage.objects for update
to authenticated
using (
    bucket_id = 'biorecord-photos'
    and split_part(name, '/', 1) = auth.uid()::text
)
with check (
    bucket_id = 'biorecord-photos'
    and split_part(name, '/', 1) = auth.uid()::text
);

create policy "Users can delete their own BioRecord photos"
on storage.objects for delete
to authenticated
using (
    bucket_id = 'biorecord-photos'
    and split_part(name, '/', 1) = auth.uid()::text
);
