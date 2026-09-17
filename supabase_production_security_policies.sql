-- ============================================================================
-- QIVO CLEAN INSTALL: PRODUCTION SCHEMA & ROW LEVEL SECURITY (RLS) SUITE
-- Drops and recreates all secondary tables cleanly, preserves existing user profiles,
-- and applies strict production security with zero missing-relation errors.
--
-- How to apply:
--   Copy and run this entire script in your Supabase SQL Editor.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================================
-- 1. PROFILES TABLE (PRESERVED & HARDENED)
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT DEFAULT 'User',
    email TEXT,
    avatar_url TEXT DEFAULT '',
    gender TEXT DEFAULT 'Other',
    bio TEXT DEFAULT '',
    coins BIGINT DEFAULT 0,
    diamonds NUMERIC(14, 2) DEFAULT 0.00,
    is_admin BOOLEAN DEFAULT false,
    is_agent BOOLEAN DEFAULT false,
    is_coinseller BOOLEAN DEFAULT false,
    active_frame_id TEXT DEFAULT '',
    frame_expires_at TEXT DEFAULT '',
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Ensure all required columns exist on profiles if table was already present
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS coins BIGINT DEFAULT 0;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS diamonds NUMERIC(14, 2) DEFAULT 0.00;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_admin BOOLEAN DEFAULT false;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_agent BOOLEAN DEFAULT false;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_coinseller BOOLEAN DEFAULT false;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS active_frame_id TEXT DEFAULT '';
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS frame_expires_at TEXT DEFAULT '';

-- Profiles RLS
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "profiles_select_policy" ON public.profiles;
CREATE POLICY "profiles_select_policy"
ON public.profiles FOR SELECT
TO anon, authenticated
USING (true);

DROP POLICY IF EXISTS "profiles_insert_policy" ON public.profiles;
CREATE POLICY "profiles_insert_policy"
ON public.profiles FOR INSERT
TO authenticated
WITH CHECK (auth.uid()::text = id::text);

DROP POLICY IF EXISTS "profiles_update_policy" ON public.profiles;
CREATE POLICY "profiles_update_policy"
ON public.profiles FOR UPDATE
TO authenticated
USING (auth.uid()::text = id::text)
WITH CHECK (auth.uid()::text = id::text);

-- Grant profile updates for authenticated users on their own profile
GRANT UPDATE ON public.profiles TO authenticated;

-- Balance anti-tamper trigger: Silently and strictly neutralizes any client-side attempt to modify
-- coins, diamonds, or role permissions (is_admin, is_agent, is_coinseller) on direct REST updates.
CREATE OR REPLACE FUNCTION public.protect_profile_financials()
RETURNS TRIGGER AS $$
BEGIN
    -- Allow internal system, postgres, and service_role operations to update financials
    IF current_user IN ('postgres', 'service_role') OR (current_setting('role', true) = 'service_role') THEN
        RETURN NEW;
    END IF;

    -- Anti-tamper: Preserve authoritative server balances regardless of client payload
    IF OLD.coins IS DISTINCT FROM NEW.coins THEN
        NEW.coins := OLD.coins;
    END IF;

    IF OLD.diamonds IS DISTINCT FROM NEW.diamonds THEN
        NEW.diamonds := OLD.diamonds;
    END IF;

    -- Anti-tamper: Preserve authoritative roles (cannot be self-elevated)
    IF OLD.is_admin IS DISTINCT FROM NEW.is_admin THEN
        NEW.is_admin := OLD.is_admin;
    END IF;
    IF OLD.is_agent IS DISTINCT FROM NEW.is_agent THEN
        NEW.is_agent := OLD.is_agent;
    END IF;
    IF OLD.is_coinseller IS DISTINCT FROM NEW.is_coinseller THEN
        NEW.is_coinseller := OLD.is_coinseller;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_protect_profile_financials ON public.profiles;
CREATE TRIGGER trg_protect_profile_financials
BEFORE UPDATE ON public.profiles
FOR EACH ROW
EXECUTE FUNCTION public.protect_profile_financials();


-- ============================================================================
-- 2. PESAPAL TRANSACTIONS TABLE
-- ============================================================================
DROP TABLE IF EXISTS public.pesapal_transactions CASCADE;

CREATE TABLE public.pesapal_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    amount NUMERIC NOT NULL DEFAULT 0,
    currency TEXT DEFAULT 'KES',
    coins BIGINT DEFAULT 0,
    order_tracking_id TEXT,
    reference TEXT,
    status TEXT DEFAULT 'PENDING',
    payment_method TEXT DEFAULT 'PESAPAL',
    coins_credited BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.pesapal_transactions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "pesapal_select_own"
ON public.pesapal_transactions FOR SELECT
TO authenticated
USING (auth.uid()::text = user_id::text);

CREATE POLICY "pesapal_insert_pending"
ON public.pesapal_transactions FOR INSERT
TO authenticated
WITH CHECK (auth.uid()::text = user_id::text AND status = 'PENDING');

CREATE POLICY "pesapal_deny_client_update"
ON public.pesapal_transactions FOR UPDATE
TO authenticated, anon
USING (false);


-- ============================================================================
-- 3. COIN & DIAMOND TRANSACTION AUDIT LOGS
-- ============================================================================
DROP TABLE IF EXISTS public.coin_transactions CASCADE;

CREATE TABLE public.coin_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL DEFAULT 0,
    type TEXT NOT NULL DEFAULT 'GENERAL',
    title TEXT DEFAULT '',
    description TEXT DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.coin_transactions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "coin_tx_select_own"
ON public.coin_transactions FOR SELECT
TO authenticated
USING (auth.uid()::text = user_id::text);

CREATE POLICY "coin_tx_deny_client_insert"
ON public.coin_transactions FOR INSERT
TO authenticated, anon
WITH CHECK (false);

CREATE POLICY "coin_tx_deny_client_update"
ON public.coin_transactions FOR UPDATE
TO authenticated, anon
USING (false);

CREATE POLICY "coin_tx_deny_client_delete"
ON public.coin_transactions FOR DELETE
TO authenticated, anon
USING (false);

DROP TABLE IF EXISTS public.diamond_transactions CASCADE;

CREATE TABLE public.diamond_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    amount NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    balance_after NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    type TEXT NOT NULL DEFAULT 'GENERAL',
    title TEXT DEFAULT '',
    description TEXT DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.diamond_transactions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "diamond_tx_select_own"
ON public.diamond_transactions FOR SELECT
TO authenticated
USING (auth.uid()::text = user_id::text);

CREATE POLICY "diamond_tx_deny_client_insert"
ON public.diamond_transactions FOR INSERT
TO authenticated, anon
WITH CHECK (false);


-- ============================================================================
-- 4. DIRECT MESSAGES TABLE
-- ============================================================================
DROP TABLE IF EXISTS public.direct_messages CASCADE;

CREATE TABLE public.direct_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id TEXT NOT NULL,
    recipient_id TEXT NOT NULL,
    content TEXT DEFAULT '',
    type TEXT DEFAULT 'TEXT',
    is_read BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.direct_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "dm_select_participants"
ON public.direct_messages FOR SELECT
TO authenticated
USING (auth.uid()::text = sender_id::text OR auth.uid()::text = recipient_id::text);

CREATE POLICY "dm_insert_sender"
ON public.direct_messages FOR INSERT
TO authenticated
WITH CHECK (auth.uid()::text = sender_id::text);

CREATE POLICY "dm_update_participants"
ON public.direct_messages FOR UPDATE
TO authenticated
USING (auth.uid()::text = sender_id::text OR auth.uid()::text = recipient_id::text);

CREATE POLICY "dm_delete_sender"
ON public.direct_messages FOR DELETE
TO authenticated
USING (auth.uid()::text = sender_id::text);


-- ============================================================================
-- 5. PARTY ROOMS, MEMBERS, AND ROOM MESSAGES
-- ============================================================================
DROP TABLE IF EXISTS public.party_room_messages CASCADE;
DROP TABLE IF EXISTS public.party_room_members CASCADE;
DROP TABLE IF EXISTS public.party_rooms CASCADE;

CREATE TABLE public.party_rooms (
    id TEXT PRIMARY KEY,
    host_id TEXT NOT NULL,
    title TEXT NOT NULL DEFAULT 'Party Room',
    announcement TEXT DEFAULT '',
    category TEXT DEFAULT 'CHAT',
    background_url TEXT DEFAULT '',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.party_rooms ENABLE ROW LEVEL SECURITY;

CREATE POLICY "party_rooms_select"
ON public.party_rooms FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "party_rooms_insert_host"
ON public.party_rooms FOR INSERT
TO authenticated
WITH CHECK (auth.uid()::text = host_id::text);

CREATE POLICY "party_rooms_update_host"
ON public.party_rooms FOR UPDATE
TO authenticated
USING (auth.uid()::text = host_id::text);

CREATE TABLE public.party_room_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    role TEXT DEFAULT 'MEMBER',
    joined_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.party_room_members ENABLE ROW LEVEL SECURITY;

CREATE POLICY "party_members_select"
ON public.party_room_members FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "party_members_insert_self"
ON public.party_room_members FOR INSERT
TO authenticated
WITH CHECK (auth.uid()::text = user_id::text);

CREATE POLICY "party_members_delete_self"
ON public.party_room_members FOR DELETE
TO authenticated
USING (auth.uid()::text = user_id::text);

CREATE TABLE public.party_room_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    user_name TEXT DEFAULT '',
    message TEXT DEFAULT '',
    type TEXT DEFAULT 'CHAT',
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.party_room_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "party_messages_select"
ON public.party_room_messages FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "party_messages_insert"
ON public.party_room_messages FOR INSERT
TO authenticated
WITH CHECK (auth.uid()::text = user_id::text);


-- ============================================================================
-- 6. FOLLOWS & PROFILE VISITORS
-- ============================================================================
DROP TABLE IF EXISTS public.user_follows CASCADE;

CREATE TABLE public.user_follows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    follower_id TEXT NOT NULL,
    following_id TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.user_follows ENABLE ROW LEVEL SECURITY;

CREATE POLICY "follows_select"
ON public.user_follows FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "follows_insert_self"
ON public.user_follows FOR INSERT
TO authenticated
WITH CHECK (auth.uid()::text = follower_id::text);

CREATE POLICY "follows_delete_self"
ON public.user_follows FOR DELETE
TO authenticated
USING (auth.uid()::text = follower_id::text);

DROP TABLE IF EXISTS public.profile_visitors CASCADE;

CREATE TABLE public.profile_visitors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    visited_id TEXT NOT NULL,
    visitor_id TEXT NOT NULL,
    visited_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.profile_visitors ENABLE ROW LEVEL SECURITY;

CREATE POLICY "visitors_select"
ON public.profile_visitors FOR SELECT
TO authenticated
USING (auth.uid()::text = visited_id::text OR auth.uid()::text = visitor_id::text);

CREATE POLICY "visitors_insert_self"
ON public.profile_visitors FOR INSERT
TO authenticated
WITH CHECK (auth.uid()::text = visitor_id::text);


-- ============================================================================
-- 7. BLOCKED USERS & MODERATION REPORTS
-- ============================================================================
DROP TABLE IF EXISTS public.blocked_users CASCADE;

CREATE TABLE public.blocked_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id TEXT NOT NULL,
    blocked_id TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.blocked_users ENABLE ROW LEVEL SECURITY;

CREATE POLICY "blocked_select_own"
ON public.blocked_users FOR SELECT
TO authenticated
USING (auth.uid()::text = blocker_id::text);

CREATE POLICY "blocked_insert_own"
ON public.blocked_users FOR INSERT
TO authenticated
WITH CHECK (auth.uid()::text = blocker_id::text);

CREATE POLICY "blocked_delete_own"
ON public.blocked_users FOR DELETE
TO authenticated
USING (auth.uid()::text = blocker_id::text);

DROP TABLE IF EXISTS public.user_reports CASCADE;

CREATE TABLE public.user_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id TEXT NOT NULL,
    reported_user_id TEXT NOT NULL,
    reason TEXT NOT NULL,
    details TEXT DEFAULT '',
    proof_url TEXT DEFAULT '',
    status TEXT DEFAULT 'PENDING',
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.user_reports ENABLE ROW LEVEL SECURITY;

CREATE POLICY "reports_insert_self"
ON public.user_reports FOR INSERT
TO authenticated
WITH CHECK (auth.uid()::text = reporter_id::text);

CREATE POLICY "reports_select"
ON public.user_reports FOR SELECT
TO authenticated
USING (
    auth.uid()::text = reporter_id::text 
    OR EXISTS (
        SELECT 1 FROM public.profiles 
        WHERE id::text = auth.uid()::text AND is_admin = true
    )
);


-- ============================================================================
-- 8. USER AVATAR FRAMES
-- ============================================================================
DROP TABLE IF EXISTS public.user_frames CASCADE;

CREATE TABLE public.user_frames (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    frame_id TEXT NOT NULL,
    frame_name TEXT DEFAULT '',
    is_equipped BOOLEAN DEFAULT false,
    expires_at TEXT DEFAULT '',
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.user_frames ENABLE ROW LEVEL SECURITY;

CREATE POLICY "frames_select"
ON public.user_frames FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "frames_update_own"
ON public.user_frames FOR UPDATE
TO authenticated
USING (auth.uid()::text = user_id::text)
WITH CHECK (auth.uid()::text = user_id::text);

CREATE POLICY "frames_deny_direct_insert"
ON public.user_frames FOR INSERT
TO authenticated
WITH CHECK (false);


-- ============================================================================
-- 9. AGENCY SUITE (AGENCIES, APPLICATIONS, MEMBERS, GROUP CHAT)
-- ============================================================================
DROP TABLE IF EXISTS public.agency_group_messages CASCADE;
DROP TABLE IF EXISTS public.agency_members CASCADE;
DROP TABLE IF EXISTS public.agency_applications CASCADE;
DROP TABLE IF EXISTS public.agencies CASCADE;

CREATE TABLE public.agencies (
    id TEXT PRIMARY KEY,
    agent_id TEXT NOT NULL,
    agency_name TEXT NOT NULL,
    agency_code TEXT UNIQUE,
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.agencies ENABLE ROW LEVEL SECURITY;

CREATE POLICY "agencies_select"
ON public.agencies FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "agencies_update_agent"
ON public.agencies FOR UPDATE
TO authenticated
USING (auth.uid()::text = agent_id::text);

CREATE TABLE public.agency_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id TEXT NOT NULL,
    agency_name TEXT DEFAULT '',
    agency_code TEXT DEFAULT '',
    user_id TEXT NOT NULL,
    user_name TEXT DEFAULT '',
    user_avatar TEXT DEFAULT '',
    status TEXT DEFAULT 'PENDING',
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.agency_applications ENABLE ROW LEVEL SECURITY;

CREATE POLICY "agency_apps_insert_self"
ON public.agency_applications FOR INSERT
TO authenticated
WITH CHECK (auth.uid()::text = user_id::text);

CREATE POLICY "agency_apps_select"
ON public.agency_applications FOR SELECT
TO authenticated
USING (
    auth.uid()::text = user_id::text 
    OR EXISTS (
        SELECT 1 FROM public.agencies 
        WHERE id::text = agency_applications.agency_id::text AND agent_id::text = auth.uid()::text
    )
);

CREATE TABLE public.agency_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id TEXT NOT NULL,
    agency_name TEXT DEFAULT '',
    agency_code TEXT DEFAULT '',
    user_id TEXT NOT NULL,
    user_name TEXT DEFAULT '',
    user_avatar TEXT DEFAULT '',
    status TEXT DEFAULT 'APPROVED',
    joined_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.agency_members ENABLE ROW LEVEL SECURITY;

CREATE POLICY "agency_members_select"
ON public.agency_members FOR SELECT
TO anon, authenticated
USING (true);

CREATE TABLE public.agency_group_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id TEXT NOT NULL,
    sender_id TEXT NOT NULL,
    sender_name TEXT DEFAULT '',
    sender_avatar TEXT DEFAULT '',
    message TEXT DEFAULT '',
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.agency_group_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "agency_msgs_select"
ON public.agency_group_messages FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.agency_members 
        WHERE agency_id::text = agency_group_messages.agency_id::text AND user_id::text = auth.uid()::text
    )
    OR EXISTS (
        SELECT 1 FROM public.agencies 
        WHERE id::text = agency_group_messages.agency_id::text AND agent_id::text = auth.uid()::text
    )
);

CREATE POLICY "agency_msgs_insert"
ON public.agency_group_messages FOR INSERT
TO authenticated
WITH CHECK (
    auth.uid()::text = sender_id::text
    AND (
        EXISTS (
            SELECT 1 FROM public.agency_members 
            WHERE agency_id::text = agency_group_messages.agency_id::text AND user_id::text = auth.uid()::text
        )
        OR EXISTS (
            SELECT 1 FROM public.agencies 
            WHERE id::text = agency_group_messages.agency_id::text AND agent_id::text = auth.uid()::text
        )
    )
);

-- ============================================================================
-- Clean setup completed successfully!
-- ============================================================================
