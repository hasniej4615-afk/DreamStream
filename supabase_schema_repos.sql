-- ====================================================================
-- DREAMSTREAM / CLOUDSTREAM REPOSITORY & PROVIDER SCHEMA FOR SUPABASE
-- Run this script in your Supabase SQL Editor: https://supabase.com/dashboard/project/seyzzmivcppoadgwkjmh/sql
-- ====================================================================

-- 1. Repositories Table
create table if not exists repositories (
    id text primary key,
    name text not null,
    description text default '',
    author text default 'DreamStream Community',
    icon_url text default '',
    url text default '',
    is_official boolean default false,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    updated_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- 2. Providers / Extensions Table
create table if not exists providers (
    id text primary key,
    repo_id text references repositories(id) on delete cascade,
    name text not null,
    display_name text not null,
    description text default '',
    author text default '',
    version integer default 1,
    version_name text default '1.0.0',
    icon_url text default '',
    media_type text default 'MULTI',            -- MOVIE, SERIES, ANIME, MULTI
    engine_type text default 'TEMPLATE',         -- TEMPLATE, DECLARATIVE, DEX
    template_type text default 'WORDPRESS_MUVIPRO', -- WORDPRESS_MUVIPRO, PENCURI, DUTAFILM, GENERIC_HTML
    base_urls jsonb default '[]'::jsonb,         -- Array of candidate domains/mirrors
    config jsonb default '{}'::jsonb,            -- Selectors, endpoints, headers, search paths
    plugin_url text default '',                  -- Optional .dex URL in Supabase Storage
    status text default 'ACTIVE',                -- ACTIVE, MAINTENANCE, DEPRECATED
    is_enabled_default boolean default true,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    updated_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- 3. Row Level Security (RLS)
alter table repositories enable row level security;
alter table providers enable row level security;

-- Drop existing policies if re-running
drop policy if exists "Allow public read repositories" on repositories;
drop policy if exists "Allow public read providers" on providers;
drop policy if exists "Allow public manage repositories" on repositories;
drop policy if exists "Allow public manage providers" on providers;

-- Policies for public reading
create policy "Allow public read repositories" on repositories for select using (true);
create policy "Allow public read providers" on providers for select using (true);

-- Allow public manage with anon key
create policy "Allow public manage repositories" on repositories for all using (true) with check (true);
create policy "Allow public manage providers" on providers for all using (true) with check (true);

-- 4. Seed Official DreamStream Repository
insert into repositories (id, name, description, author, icon_url, url, is_official)
values (
    'dreamstream-official',
    'DreamStream Official Repo',
    'Official repository featuring curated, high-speed streaming sources for Movies, Series, and Asian Dramas.',
    'DreamStream Team',
    'https://raw.githubusercontent.com/hasniej4615-afk/DreamStream/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png',
    'https://seyzzmivcppoadgwkjmh.supabase.co/rest/v1/providers?repo_id=eq.dreamstream-official',
    true
) on conflict (id) do update set
    name = excluded.name,
    description = excluded.description,
    icon_url = excluded.icon_url,
    updated_at = timezone('utc'::text, now());

-- 5. Seed Built-In Official Providers

-- Provider 1: PencuriMovie
insert into providers (
    id, repo_id, name, display_name, description, author, version, version_name,
    icon_url, media_type, engine_type, template_type, base_urls, config, status, is_enabled_default
) values (
    'com.duta.provider.pencurimovie',
    'dreamstream-official',
    'PencuriMovie',
    'PencuriMovie (Malay & Cinema)',
    'High-speed Malay, Asian, and Hollywood movies with direct HLS streaming.',
    'DreamStream Team',
    1,
    '1.0.0',
    'https://raw.githubusercontent.com/hasniej4615-afk/DreamStream/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png',
    'MULTI',
    'TEMPLATE',
    'PENCURI',
    '["https://ww44.pencurimovie.baby", "https://pencurimovie.baby", "https://pencurimovie.xyz", "https://ww45.pencurimovie.baby"]'::jsonb,
    '{"searchPath": "/?s=", "isSeriesSupported": true}'::jsonb,
    'ACTIVE',
    true
) on conflict (id) do update set
    base_urls = excluded.base_urls,
    config = excluded.config,
    updated_at = timezone('utc'::text, now());

-- Provider 2: DutaFilm
insert into providers (
    id, repo_id, name, display_name, description, author, version, version_name,
    icon_url, media_type, engine_type, template_type, base_urls, config, status, is_enabled_default
) values (
    'com.duta.provider.dutafilm',
    'dreamstream-official',
    'DutaFilm',
    'DutaFilm (Movies & TV Series)',
    'Comprehensive movie and TV series library with multi-resolution streaming.',
    'DreamStream Team',
    1,
    '1.0.0',
    'https://raw.githubusercontent.com/hasniej4615-afk/DreamStream/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png',
    'MULTI',
    'TEMPLATE',
    'DUTAFILM',
    '["http://159.89.249.45", "https://df31.mantab.men", "https://df32.mantab.men"]'::jsonb,
    '{"searchPath": "/search/", "isSeriesSupported": true}'::jsonb,
    'ACTIVE',
    true
) on conflict (id) do update set
    base_urls = excluded.base_urls,
    config = excluded.config,
    updated_at = timezone('utc'::text, now());

-- Provider 3: LK21 / Bullerswood
insert into providers (
    id, repo_id, name, display_name, description, author, version, version_name,
    icon_url, media_type, engine_type, template_type, base_urls, config, status, is_enabled_default
) values (
    'com.duta.provider.lk21',
    'dreamstream-official',
    'LK21 Bullerswood',
    'LK21 / LayarKaca21',
    'Extensive collection of Indonesian and international cinema and dramas.',
    'DreamStream Team',
    1,
    '1.0.0',
    'https://raw.githubusercontent.com/hasniej4615-afk/DreamStream/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png',
    'MOVIE',
    'TEMPLATE',
    'WORDPRESS_MUVIPRO',
    '["https://bullerswood.org", "https://scphi.org", "https://grishamfarms.org"]'::jsonb,
    '{"searchPath": "/?s=", "isSeriesSupported": false}'::jsonb,
    'ACTIVE',
    true
) on conflict (id) do update set
    base_urls = excluded.base_urls,
    config = excluded.config,
    updated_at = timezone('utc'::text, now());

-- Provider 4: P-Ramlee Classic Archive
insert into providers (
    id, repo_id, name, display_name, description, author, version, version_name,
    icon_url, media_type, engine_type, template_type, base_urls, config, status, is_enabled_default
) values (
    'com.duta.provider.pramlee',
    'dreamstream-official',
    'P-Ramlee Archive',
    'Koleksi Filem P. Ramlee',
    'Heritage collection of classic Tan Sri P. Ramlee masterpieces and Shaw Brothers classics.',
    'DreamStream Heritage',
    1,
    '1.0.0',
    'https://raw.githubusercontent.com/hasniej4615-afk/DreamStream/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png',
    'MOVIE',
    'TEMPLATE',
    'GENERIC_HTML',
    '["https://archive.org"]'::jsonb,
    '{"archiveCollection": "FilemP.ramlee"}'::jsonb,
    'ACTIVE',
    true
) on conflict (id) do update set
    base_urls = excluded.base_urls,
    config = excluded.config,
    updated_at = timezone('utc'::text, now());
