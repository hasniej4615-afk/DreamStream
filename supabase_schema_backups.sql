-- ====================================================================
-- DREAMSTREAM USER CLOUD BACKUP SCHEMA FOR SUPABASE
-- Run this script in your Supabase SQL Editor:
-- https://supabase.com/dashboard/project/seyzzmivcppoadgwkjmh/sql
-- ====================================================================

-- 1. Create user_backups table
CREATE TABLE IF NOT EXISTS public.user_backups (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    pin_code text UNIQUE NOT NULL,
    username text DEFAULT '',
    backup_data jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at timestamp with time zone DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 2. Create index on pin_code for instant lookup
CREATE INDEX IF NOT EXISTS idx_user_backups_pin ON public.user_backups(pin_code);

-- 3. Enable Row Level Security (RLS)
ALTER TABLE public.user_backups ENABLE ROW LEVEL SECURITY;

-- Drop existing policies if re-running
DROP POLICY IF EXISTS "Allow public read backups by pin" ON public.user_backups;
DROP POLICY IF EXISTS "Allow public insert backups" ON public.user_backups;
DROP POLICY IF EXISTS "Allow public update backups by pin" ON public.user_backups;
DROP POLICY IF EXISTS "Allow public delete backups" ON public.user_backups;

-- 4. Policies for public anon key access
CREATE POLICY "Allow public read backups by pin" ON public.user_backups
    FOR SELECT USING (true);

CREATE POLICY "Allow public insert backups" ON public.user_backups
    FOR INSERT WITH CHECK (true);

CREATE POLICY "Allow public update backups by pin" ON public.user_backups
    FOR UPDATE USING (true) WITH CHECK (true);

CREATE POLICY "Allow public delete backups" ON public.user_backups
    FOR DELETE USING (true);
