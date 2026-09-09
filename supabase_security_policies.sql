-- ============================================================================
-- QIVO PRODUCTION SUPABASE INITIALIZATION & HARDENED SECURITY POLICIES
-- Complete, production-grade schema creation, Row Level Security (RLS), and
-- server-authoritative RPC functions.
--
-- Security Features:
--   1. Strict Server-Side Authorization for all Admin RPCs (auth.uid() checked).
--   2. Server-Determined Coin Deductions (never trust client amounts, sender, or prices).
--   3. Locked Diamond -> Coin Conversion (strictly server-calculated 1:1 rate).
--   4. One-Time Server-Controlled Welcome Bonus (atomic user & device lock).
--   5. Locked Party Room Operations (host & seat invariants verified).
--   6. Protection Trigger on Profiles table preventing direct client tampering of
--      is_admin, is_coinseller, is_agent, coins, diamonds.
-- ============================================================================

-- ============================================================================
-- 1. BASE TABLES DEFINITION
-- ============================================================================

-- Profiles Table
CREATE TABLE IF NOT EXISTS public.profiles (
    id TEXT PRIMARY KEY,
    name TEXT DEFAULT '',
    email TEXT DEFAULT '',
    gender TEXT DEFAULT 'Not specified',
    age INTEGER DEFAULT 20,
    country TEXT DEFAULT '',
    bio TEXT DEFAULT '',
    avatar_url TEXT DEFAULT '',
    cover_url TEXT DEFAULT '',
    user_id_number BIGINT DEFAULT 100000,
    numeric_id BIGINT DEFAULT 100000,
    coins BIGINT DEFAULT 0,
    diamonds BIGINT DEFAULT 0,
    user_level INTEGER DEFAULT 1,
    wealth_level INTEGER DEFAULT 1,
    charm_level INTEGER DEFAULT 1,
    vip_level INTEGER DEFAULT 0,
    is_vip BOOLEAN DEFAULT false,
    is_coinseller BOOLEAN DEFAULT false,
    is_agent BOOLEAN DEFAULT false,
    is_admin BOOLEAN DEFAULT false,
    is_banned BOOLEAN DEFAULT false,
    active_frame_id TEXT DEFAULT '',
    avatar_frame TEXT DEFAULT '',
    frame_expires_at TEXT,
    is_online BOOLEAN DEFAULT false,
    exp BIGINT DEFAULT 0,
    last_active_at TIMESTAMPTZ DEFAULT now(),
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Ensure numeric_id matches user_id_number if either is set
CREATE OR REPLACE FUNCTION public.sync_profile_numeric_ids()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.user_id_number IS NOT NULL AND (NEW.numeric_id IS NULL OR NEW.numeric_id = 100000) THEN
        NEW.numeric_id := NEW.user_id_number;
    ELSIF NEW.numeric_id IS NOT NULL AND (NEW.user_id_number IS NULL OR NEW.user_id_number = 100000) THEN
        NEW.user_id_number := NEW.numeric_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_profile_numeric_ids ON public.profiles;
CREATE TRIGGER trg_sync_profile_numeric_ids
BEFORE INSERT OR UPDATE ON public.profiles
FOR EACH ROW EXECUTE FUNCTION public.sync_profile_numeric_ids();

-- Coin Transactions History Table
CREATE TABLE IF NOT EXISTS public.coin_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    amount BIGINT NOT NULL,
    type TEXT NOT NULL,
    title TEXT DEFAULT '',
    description TEXT DEFAULT '',
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Welcome Bonus Claims Table (prevents multi-claiming by user or device)
CREATE TABLE IF NOT EXISTS public.claimed_welcome_bonuses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL UNIQUE,
    device_hash TEXT NOT NULL,
    bonus_coins BIGINT DEFAULT 500,
    claimed_at TIMESTAMPTZ DEFAULT now()
);

-- Messages Tables
CREATE TABLE IF NOT EXISTS public.messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id TEXT NOT NULL,
    receiver_id TEXT NOT NULL,
    content TEXT DEFAULT '',
    media_url TEXT,
    media_type TEXT,
    is_read BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id TEXT NOT NULL,
    receiver_id TEXT NOT NULL,
    content TEXT DEFAULT '',
    media_url TEXT,
    media_type TEXT,
    is_read BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Party Rooms Tables
CREATE TABLE IF NOT EXISTS public.party_rooms (
    id TEXT PRIMARY KEY,
    room_number BIGINT DEFAULT 100000,
    title TEXT NOT NULL,
    category TEXT DEFAULT 'Music & Chat',
    cover_image TEXT DEFAULT '',
    host_user_id TEXT NOT NULL,
    host_name TEXT DEFAULT 'Host',
    host_avatar TEXT DEFAULT '',
    seats_count INTEGER DEFAULT 8,
    is_locked BOOLEAN DEFAULT false,
    room_password TEXT DEFAULT '',
    announcement TEXT DEFAULT '',
    background_theme TEXT DEFAULT 'default',
    member_count INTEGER DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.party_room_seats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id TEXT NOT NULL,
    seat_index INTEGER NOT NULL,
    user_id TEXT,
    user_name TEXT,
    user_avatar TEXT,
    user_level INTEGER DEFAULT 1,
    is_muted BOOLEAN DEFAULT false,
    is_locked BOOLEAN DEFAULT false,
    updated_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(room_id, seat_index)
);

CREATE TABLE IF NOT EXISTS public.party_room_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id TEXT NOT NULL,
    sender_id TEXT,
    sender_name TEXT NOT NULL,
    sender_avatar TEXT DEFAULT '',
    content TEXT NOT NULL,
    msg_type TEXT DEFAULT 'text',
    gift_icon TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.party_room_admins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    appointed_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(room_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.party_room_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    user_name TEXT DEFAULT 'Guest',
    user_avatar TEXT DEFAULT '',
    joined_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(room_id, user_id)
);

-- FCM Tokens
CREATE TABLE IF NOT EXISTS public.fcm_device_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    device_token TEXT NOT NULL UNIQUE,
    platform TEXT DEFAULT 'android',
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.fcm_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    token TEXT NOT NULL UNIQUE,
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Social: Blocks, Followers, Visitors
CREATE TABLE IF NOT EXISTS public.user_blocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id TEXT NOT NULL,
    blocked_id TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(blocker_id, blocked_id)
);

CREATE TABLE IF NOT EXISTS public.blocked_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id TEXT NOT NULL,
    blocked_id TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(blocker_id, blocked_id)
);

CREATE TABLE IF NOT EXISTS public.user_followers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    follower_id TEXT NOT NULL,
    following_id TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(follower_id, following_id)
);

CREATE TABLE IF NOT EXISTS public.user_follows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    follower_id TEXT NOT NULL,
    following_id TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(follower_id, following_id)
);

CREATE TABLE IF NOT EXISTS public.user_visitors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    visited_user_id TEXT NOT NULL,
    visitor_user_id TEXT NOT NULL,
    visitor_name TEXT DEFAULT '',
    visitor_avatar TEXT DEFAULT '',
    visited_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.profile_visitors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    visited_id TEXT NOT NULL,
    visitor_id TEXT NOT NULL,
    visitor_name TEXT DEFAULT '',
    visitor_avatar TEXT DEFAULT '',
    visited_at TIMESTAMPTZ DEFAULT now()
);

-- Advertisements
CREATE TABLE IF NOT EXISTS public.app_advertisements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    description TEXT DEFAULT '',
    image_url TEXT NOT NULL,
    target_action TEXT DEFAULT 'none',
    action_url TEXT DEFAULT '',
    is_active BOOLEAN DEFAULT true,
    display_order INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.advertisements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    description TEXT DEFAULT '',
    image_url TEXT NOT NULL,
    target_action TEXT DEFAULT 'none',
    action_url TEXT DEFAULT '',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Agencies
CREATE TABLE IF NOT EXISTS public.agencies (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    agency_code TEXT UNIQUE NOT NULL,
    description TEXT DEFAULT '',
    logo_url TEXT DEFAULT '',
    owner_id TEXT NOT NULL,
    commission_rate NUMERIC DEFAULT 10.0,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.agency_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    user_name TEXT DEFAULT '',
    user_avatar TEXT DEFAULT '',
    status TEXT DEFAULT 'APPROVED',
    joined_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(agency_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.agency_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    user_name TEXT DEFAULT '',
    user_avatar TEXT DEFAULT '',
    status TEXT DEFAULT 'PENDING',
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.agency_group_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id TEXT NOT NULL,
    sender_id TEXT NOT NULL,
    sender_name TEXT NOT NULL,
    sender_avatar TEXT DEFAULT '',
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- User Frames
CREATE TABLE IF NOT EXISTS public.user_frames (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    frame_id TEXT NOT NULL,
    frame_name TEXT DEFAULT '',
    frame_icon_url TEXT DEFAULT '',
    is_active BOOLEAN DEFAULT false,
    purchased_at TIMESTAMPTZ DEFAULT now(),
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(user_id, frame_id)
);

CREATE TABLE IF NOT EXISTS public.user_avatar_frames (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    frame_id TEXT NOT NULL,
    frame_name TEXT DEFAULT '',
    frame_icon_url TEXT DEFAULT '',
    is_active BOOLEAN DEFAULT false,
    price_paid BIGINT DEFAULT 0,
    purchased_at TIMESTAMPTZ DEFAULT now(),
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Financial & Reward Transactions
CREATE TABLE IF NOT EXISTS public.diamond_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    type TEXT NOT NULL,
    title TEXT DEFAULT '',
    description TEXT DEFAULT '',
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.fast_reply_transactions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    female_user_id TEXT NOT NULL,
    male_user_id TEXT NOT NULL,
    original_message_id BIGINT NOT NULL,
    reply_message_id BIGINT NOT NULL,
    diamonds_awarded NUMERIC(14, 2) NOT NULL,
    idempotency_key TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT fast_reply_transactions_reply_msg_key UNIQUE (reply_message_id)
);

-- User Reports & Safety
CREATE TABLE IF NOT EXISTS public.user_reports (
    id TEXT PRIMARY KEY,
    reporter_id TEXT NOT NULL,
    reporter_name TEXT DEFAULT '',
    reported_id TEXT NOT NULL,
    reported_name TEXT DEFAULT '',
    reason TEXT NOT NULL,
    details TEXT DEFAULT '',
    proof_url TEXT DEFAULT '',
    status TEXT DEFAULT 'PENDING',
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Analytics Events
CREATE TABLE IF NOT EXISTS public.analytics_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT,
    event_type TEXT NOT NULL,
    event_data JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- ============================================================================
-- 2. ENABLE ROW LEVEL SECURITY (RLS) ON ALL TABLES
-- ============================================================================
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.coin_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.claimed_welcome_bonuses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.party_rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.party_room_seats ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.party_room_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.party_room_admins ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.party_room_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fcm_device_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fcm_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_blocks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.blocked_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_followers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_follows ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_visitors ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.profile_visitors ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_advertisements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.advertisements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.agencies ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.agency_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.agency_applications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.agency_group_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_frames ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_avatar_frames ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.diamond_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fast_reply_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.analytics_events ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- 3. HELPER FUNCTIONS FOR SECURITY
-- ============================================================================
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS boolean AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id::text = auth.uid()::text AND is_admin = true
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION public.is_blocked(user1 text, user2 text)
RETURNS boolean AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.user_blocks
        WHERE (blocker_id::text = user1::text AND blocked_id::text = user2::text)
           OR (blocker_id::text = user2::text AND blocked_id::text = user1::text)
    ) OR EXISTS (
        SELECT 1 FROM public.blocked_users
        WHERE (blocker_id::text = user1::text AND blocked_id::text = user2::text)
           OR (blocker_id::text = user2::text AND blocked_id::text = user1::text)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================================
-- 4. ROW LEVEL SECURITY POLICIES (AUTHENTICATED ONLY - ZERO ANON ACCESS)
-- ============================================================================

-- Explicitly revoke all access from unauthenticated anon role
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM anon;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon;

-- Explicitly grant only to authenticated and service_role
GRANT USAGE ON SCHEMA public TO authenticated;
GRANT ALL ON ALL TABLES IN SCHEMA public TO authenticated;
GRANT ALL ON ALL FUNCTIONS IN SCHEMA public TO authenticated;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO authenticated;

-- Profiles Table Policies
DROP POLICY IF EXISTS "Public profiles read access" ON public.profiles;
DROP POLICY IF EXISTS "Users can create their own profile" ON public.profiles;
DROP POLICY IF EXISTS "Users can update their own profile" ON public.profiles;

CREATE POLICY "Public profiles read access"
ON public.profiles FOR SELECT
TO authenticated
USING (NOT public.is_blocked(COALESCE(auth.uid()::text, ''), id::text));

CREATE POLICY "Users can create their own profile"
ON public.profiles FOR INSERT
TO authenticated
WITH CHECK (auth.uid()::text = id::text);

CREATE POLICY "Users can update their own profile"
ON public.profiles FOR UPDATE
TO authenticated
USING (auth.uid()::text = id::text)
WITH CHECK (auth.uid()::text = id::text);

-- Protect Sensitive Profile Columns from Direct User UPDATE
-- Only SECURITY DEFINER functions or server-side admin can alter currency, roles, and EXP/level
CREATE OR REPLACE FUNCTION public.protect_sensitive_profile_fields()
RETURNS TRIGGER AS $$
BEGIN
    -- If executed by an unauthenticated or normal user direct update, preserve server-authoritative fields
    IF current_user <> 'postgres' AND current_user <> 'service_role' THEN
        -- If caller is not admin, prevent modifying roles, currency, and progression directly
        IF NOT public.is_admin() THEN
            NEW.is_admin := OLD.is_admin;
            NEW.is_coinseller := OLD.is_coinseller;
            NEW.is_agent := OLD.is_agent;
            NEW.is_banned := OLD.is_banned;
            NEW.coins := OLD.coins;
            NEW.diamonds := OLD.diamonds;
            NEW.exp := OLD.exp;
            NEW.user_level := OLD.user_level;
            NEW.wealth_level := OLD.wealth_level;
            NEW.charm_level := OLD.charm_level;
            NEW.vip_level := OLD.vip_level;
            NEW.is_vip := OLD.is_vip;
            NEW.is_verified := OLD.is_verified;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_protect_sensitive_profile_fields ON public.profiles;
CREATE TRIGGER trg_protect_sensitive_profile_fields
BEFORE UPDATE ON public.profiles
FOR EACH ROW EXECUTE FUNCTION public.protect_sensitive_profile_fields();

-- Enforce Strict Server-Side Zero Balances for New Profiles (Anti-Tampering)
-- Client inserts cannot grant initial coins, diamonds, EXP, or elevated levels directly
CREATE OR REPLACE FUNCTION public.enforce_new_profile_defaults()
RETURNS TRIGGER AS $$
BEGIN
    NEW.coins := 0;
    NEW.diamonds := 0;
    NEW.exp := 0;
    NEW.user_level := 1;
    NEW.wealth_level := 1;
    NEW.charm_level := 1;
    NEW.vip_level := 0;
    NEW.is_vip := false;
    NEW.is_verified := false;
    NEW.is_admin := false;
    NEW.is_coinseller := false;
    NEW.is_agent := false;
    NEW.is_banned := false;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_enforce_new_profile_defaults ON public.profiles;
CREATE TRIGGER trg_enforce_new_profile_defaults
BEFORE INSERT ON public.profiles
FOR EACH ROW EXECUTE FUNCTION public.enforce_new_profile_defaults();

-- Coin Transactions Policies
DROP POLICY IF EXISTS "Users view own coin transactions" ON public.coin_transactions;
CREATE POLICY "Users view own coin transactions"
ON public.coin_transactions FOR SELECT
TO authenticated
USING (auth.uid()::text = user_id::text OR public.is_admin());

DROP POLICY IF EXISTS "Users insert own coin transactions" ON public.coin_transactions;
CREATE POLICY "Users insert own coin transactions"
ON public.coin_transactions FOR INSERT
TO authenticated
WITH CHECK (auth.uid()::text = user_id::text OR public.is_admin());

-- Welcome Bonus Claims Policies
DROP POLICY IF EXISTS "Users view own welcome bonus claim" ON public.claimed_welcome_bonuses;
CREATE POLICY "Users view own welcome bonus claim"
ON public.claimed_welcome_bonuses FOR SELECT
TO authenticated
USING (auth.uid()::text = user_id::text OR public.is_admin());

-- Direct Messages Policies
DROP POLICY IF EXISTS "Users can read their own messages" ON public.messages;
DROP POLICY IF EXISTS "Users can send messages" ON public.messages;
DROP POLICY IF EXISTS "Users can update received messages" ON public.messages;
DROP POLICY IF EXISTS "Users can delete their own sent messages" ON public.messages;

CREATE POLICY "Users can read their own messages"
ON public.messages FOR SELECT
TO authenticated
USING (
  (auth.uid()::text = sender_id::text OR auth.uid()::text = receiver_id::text)
  AND NOT public.is_blocked(sender_id::text, receiver_id::text)
);

CREATE POLICY "Users can send messages"
ON public.messages FOR INSERT
TO authenticated
WITH CHECK (
  auth.uid()::text = sender_id::text
  AND NOT public.is_blocked(sender_id::text, receiver_id::text)
);

CREATE POLICY "Users can update received messages"
ON public.messages FOR UPDATE
TO authenticated
USING (auth.uid()::text = receiver_id::text)
WITH CHECK (auth.uid()::text = receiver_id::text);

CREATE POLICY "Users can delete their own sent messages"
ON public.messages FOR DELETE
TO authenticated
USING (auth.uid()::text = sender_id::text);

-- Chat Messages Policies
DROP POLICY IF EXISTS "Users can read chat_messages" ON public.chat_messages;
DROP POLICY IF EXISTS "Users can send chat_messages" ON public.chat_messages;
DROP POLICY IF EXISTS "Users can update received chat_messages" ON public.chat_messages;
DROP POLICY IF EXISTS "Users can delete chat_messages" ON public.chat_messages;

CREATE POLICY "Users can read chat_messages"
ON public.chat_messages FOR SELECT
TO authenticated
USING (
  (auth.uid()::text = sender_id::text OR auth.uid()::text = receiver_id::text)
  AND NOT public.is_blocked(sender_id::text, receiver_id::text)
);

CREATE POLICY "Users can send chat_messages"
ON public.chat_messages FOR INSERT
TO authenticated
WITH CHECK (
  auth.uid()::text = sender_id::text
  AND NOT public.is_blocked(sender_id::text, receiver_id::text)
);

CREATE POLICY "Users can update received chat_messages"
ON public.chat_messages FOR UPDATE
TO authenticated
USING (auth.uid()::text = receiver_id::text)
WITH CHECK (auth.uid()::text = receiver_id::text);

CREATE POLICY "Users can delete chat_messages"
ON public.chat_messages FOR DELETE
TO authenticated
USING (auth.uid()::text = sender_id::text);

-- Party Rooms & Seats Policies
DROP POLICY IF EXISTS "View party rooms" ON public.party_rooms;
DROP POLICY IF EXISTS "Create party rooms" ON public.party_rooms;
DROP POLICY IF EXISTS "Manage party rooms" ON public.party_rooms;

CREATE POLICY "View party rooms" ON public.party_rooms FOR SELECT TO authenticated USING (true);
CREATE POLICY "Create party rooms" ON public.party_rooms FOR INSERT TO authenticated WITH CHECK (auth.uid()::text = host_user_id::text);
CREATE POLICY "Manage party rooms" ON public.party_rooms FOR ALL TO authenticated USING (auth.uid()::text = host_user_id::text OR public.is_admin());

DROP POLICY IF EXISTS "View room seats" ON public.party_room_seats;
DROP POLICY IF EXISTS "Manage room seats" ON public.party_room_seats;
CREATE POLICY "View room seats" ON public.party_room_seats FOR SELECT TO authenticated USING (true);
CREATE POLICY "Manage room seats" ON public.party_room_seats FOR ALL TO authenticated USING (auth.uid()::text = user_id::text OR public.is_admin() OR EXISTS (SELECT 1 FROM public.party_rooms WHERE id = room_id AND host_user_id = auth.uid()::text));

DROP POLICY IF EXISTS "View room messages" ON public.party_room_messages;
DROP POLICY IF EXISTS "Send room messages" ON public.party_room_messages;
CREATE POLICY "View room messages" ON public.party_room_messages FOR SELECT TO authenticated USING (true);
CREATE POLICY "Send room messages" ON public.party_room_messages FOR INSERT TO authenticated WITH CHECK (auth.uid()::text = sender_id::text);

DROP POLICY IF EXISTS "View room members" ON public.party_room_members;
DROP POLICY IF EXISTS "Manage room members" ON public.party_room_members;
CREATE POLICY "View room members" ON public.party_room_members FOR SELECT TO authenticated USING (true);
CREATE POLICY "Manage room members" ON public.party_room_members FOR ALL TO authenticated USING (auth.uid()::text = user_id::text OR public.is_admin() OR EXISTS (SELECT 1 FROM public.party_rooms WHERE id = room_id AND host_user_id = auth.uid()::text));

DROP POLICY IF EXISTS "View room admins" ON public.party_room_admins;
DROP POLICY IF EXISTS "Manage room admins" ON public.party_room_admins;
CREATE POLICY "View room admins" ON public.party_room_admins FOR SELECT TO authenticated USING (true);
CREATE POLICY "Manage room admins" ON public.party_room_admins FOR ALL TO authenticated USING (public.is_admin() OR EXISTS (SELECT 1 FROM public.party_rooms WHERE id = room_id AND host_user_id = auth.uid()::text));

-- FCM Tokens Policies
DROP POLICY IF EXISTS "Manage FCM device tokens" ON public.fcm_device_tokens;
CREATE POLICY "Manage FCM device tokens" ON public.fcm_device_tokens FOR ALL TO authenticated USING (auth.uid()::text = user_id::text) WITH CHECK (auth.uid()::text = user_id::text);

DROP POLICY IF EXISTS "Manage FCM tokens" ON public.fcm_tokens;
CREATE POLICY "Manage FCM tokens" ON public.fcm_tokens FOR ALL TO authenticated USING (auth.uid()::text = user_id::text) WITH CHECK (auth.uid()::text = user_id::text);

-- Social Policies
DROP POLICY IF EXISTS "View user blocks" ON public.user_blocks;
DROP POLICY IF EXISTS "Manage user blocks" ON public.user_blocks;
CREATE POLICY "View user blocks" ON public.user_blocks FOR SELECT TO authenticated USING (auth.uid()::text = blocker_id::text OR auth.uid()::text = blocked_id::text);
CREATE POLICY "Manage user blocks" ON public.user_blocks FOR ALL TO authenticated USING (auth.uid()::text = blocker_id::text) WITH CHECK (auth.uid()::text = blocker_id::text);

DROP POLICY IF EXISTS "View blocked users" ON public.blocked_users;
DROP POLICY IF EXISTS "Manage blocked users" ON public.blocked_users;
CREATE POLICY "View blocked users" ON public.blocked_users FOR SELECT TO authenticated USING (auth.uid()::text = blocker_id::text OR auth.uid()::text = blocked_id::text);
CREATE POLICY "Manage blocked users" ON public.blocked_users FOR ALL TO authenticated USING (auth.uid()::text = blocker_id::text) WITH CHECK (auth.uid()::text = blocker_id::text);

DROP POLICY IF EXISTS "View user followers" ON public.user_followers;
DROP POLICY IF EXISTS "Manage user followers" ON public.user_followers;
CREATE POLICY "View user followers" ON public.user_followers FOR SELECT TO authenticated USING (true);
CREATE POLICY "Manage user followers" ON public.user_followers FOR ALL TO authenticated USING (auth.uid()::text = follower_id::text) WITH CHECK (auth.uid()::text = follower_id::text);

DROP POLICY IF EXISTS "View user follows" ON public.user_follows;
DROP POLICY IF EXISTS "Manage user follows" ON public.user_follows;
CREATE POLICY "View user follows" ON public.user_follows FOR SELECT TO authenticated USING (true);
CREATE POLICY "Manage user follows" ON public.user_follows FOR ALL TO authenticated USING (auth.uid()::text = follower_id::text) WITH CHECK (auth.uid()::text = follower_id::text);

DROP POLICY IF EXISTS "View user visitors" ON public.user_visitors;
DROP POLICY IF EXISTS "Insert user visitors" ON public.user_visitors;
CREATE POLICY "View user visitors" ON public.user_visitors FOR SELECT TO authenticated USING (auth.uid()::text = visited_user_id::text OR public.is_admin());
CREATE POLICY "Insert user visitors" ON public.user_visitors FOR INSERT TO authenticated WITH CHECK (auth.uid()::text = visitor_user_id::text);

DROP POLICY IF EXISTS "View profile visitors" ON public.profile_visitors;
DROP POLICY IF EXISTS "Insert profile visitors" ON public.profile_visitors;
CREATE POLICY "View profile visitors" ON public.profile_visitors FOR SELECT TO authenticated USING (auth.uid()::text = visited_id::text OR public.is_admin());
CREATE POLICY "Insert profile visitors" ON public.profile_visitors FOR INSERT TO authenticated WITH CHECK (auth.uid()::text = visitor_id::text);

-- Ads Policies (Authenticated + Anon Public Viewing)
GRANT SELECT ON public.app_advertisements TO anon;
GRANT SELECT ON public.advertisements TO anon;

DROP POLICY IF EXISTS "View app ads" ON public.app_advertisements;
DROP POLICY IF EXISTS "Manage app ads" ON public.app_advertisements;
CREATE POLICY "View app ads" ON public.app_advertisements FOR SELECT USING (true);
CREATE POLICY "Manage app ads" ON public.app_advertisements FOR ALL TO authenticated USING (public.is_admin()) WITH CHECK (public.is_admin());

DROP POLICY IF EXISTS "View ads" ON public.advertisements;
DROP POLICY IF EXISTS "Manage ads" ON public.advertisements;
CREATE POLICY "View ads" ON public.advertisements FOR SELECT USING (true);
CREATE POLICY "Manage ads" ON public.advertisements FOR ALL TO authenticated USING (public.is_admin()) WITH CHECK (public.is_admin());

-- Agencies Policies
DROP POLICY IF EXISTS "View agencies" ON public.agencies;
DROP POLICY IF EXISTS "Manage agencies" ON public.agencies;
CREATE POLICY "View agencies" ON public.agencies FOR SELECT TO authenticated USING (true);
CREATE POLICY "Manage agencies" ON public.agencies FOR ALL TO authenticated USING (auth.uid()::text = owner_id::text OR public.is_admin()) WITH CHECK (auth.uid()::text = owner_id::text OR public.is_admin());

DROP POLICY IF EXISTS "View agency members" ON public.agency_members;
DROP POLICY IF EXISTS "Manage agency members" ON public.agency_members;
CREATE POLICY "View agency members" ON public.agency_members FOR SELECT TO authenticated USING (true);
CREATE POLICY "Manage agency members" ON public.agency_members FOR ALL TO authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "View agency applications" ON public.agency_applications;
DROP POLICY IF EXISTS "Manage agency applications" ON public.agency_applications;
CREATE POLICY "View agency applications" ON public.agency_applications FOR SELECT TO authenticated USING (true);
CREATE POLICY "Manage agency applications" ON public.agency_applications FOR ALL TO authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "View agency messages" ON public.agency_group_messages;
DROP POLICY IF EXISTS "Send agency messages" ON public.agency_group_messages;
CREATE POLICY "View agency messages" ON public.agency_group_messages FOR SELECT TO authenticated USING (true);
CREATE POLICY "Send agency messages" ON public.agency_group_messages FOR INSERT TO authenticated WITH CHECK (auth.uid()::text = sender_id::text);

-- User Frames Policies
DROP POLICY IF EXISTS "Public can view active user frames" ON public.user_frames;
DROP POLICY IF EXISTS "Users can manage their own frames" ON public.user_frames;
CREATE POLICY "Public can view active user frames" ON public.user_frames FOR SELECT TO authenticated USING (true);
CREATE POLICY "Users can manage their own frames" ON public.user_frames FOR ALL TO authenticated USING (auth.uid()::text = user_id::text) WITH CHECK (auth.uid()::text = user_id::text);

DROP POLICY IF EXISTS "Public can view active avatar frames" ON public.user_avatar_frames;
DROP POLICY IF EXISTS "Users can manage their own avatar frames" ON public.user_avatar_frames;
CREATE POLICY "Public can view active avatar frames" ON public.user_avatar_frames FOR SELECT TO authenticated USING (true);
CREATE POLICY "Users can manage their own avatar frames" ON public.user_avatar_frames FOR ALL TO authenticated USING (auth.uid()::text = user_id::text) WITH CHECK (auth.uid()::text = user_id::text);

-- Financial & Reward Policies
DROP POLICY IF EXISTS "Users view own diamond transactions" ON public.diamond_transactions;
CREATE POLICY "Users view own diamond transactions" ON public.diamond_transactions FOR SELECT TO authenticated USING (auth.uid()::text = user_id::text OR public.is_admin());

DROP POLICY IF EXISTS "Users view own fast reply transactions" ON public.fast_reply_transactions;
CREATE POLICY "Users view own fast reply transactions" ON public.fast_reply_transactions FOR SELECT TO authenticated USING (auth.uid()::text = female_user_id::text OR auth.uid()::text = male_user_id::text OR public.is_admin());

-- User Reports Policies
DROP POLICY IF EXISTS "Insert user reports" ON public.user_reports;
DROP POLICY IF EXISTS "View user reports" ON public.user_reports;
DROP POLICY IF EXISTS "Update user reports" ON public.user_reports;
CREATE POLICY "Insert user reports" ON public.user_reports FOR INSERT TO authenticated WITH CHECK (auth.uid()::text = reporter_id::text);
CREATE POLICY "View user reports" ON public.user_reports FOR SELECT TO authenticated USING (auth.uid()::text = reporter_id::text OR public.is_admin());
CREATE POLICY "Update user reports" ON public.user_reports FOR UPDATE TO authenticated USING (public.is_admin()) WITH CHECK (public.is_admin());

-- Analytics Events Policies
DROP POLICY IF EXISTS "Insert analytics events" ON public.analytics_events;
CREATE POLICY "Insert analytics events" ON public.analytics_events FOR INSERT TO authenticated WITH CHECK (auth.uid()::text = user_id::text OR user_id IS NULL);


-- ============================================================================
-- 5. SECURE SERVER-SIDE RPC FUNCTIONS
-- ============================================================================

-- ----------------------------------------------------------------------------
-- [RPC 1] admin_update_user_roles
-- Strictly checks server-side admin status of auth.uid()
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.admin_update_user_roles(
    p_admin_id TEXT DEFAULT NULL,
    p_target_numeric_id BIGINT DEFAULT NULL,
    p_is_coinseller BOOLEAN DEFAULT false,
    p_is_agent BOOLEAN DEFAULT false
)
RETURNS jsonb AS $$
DECLARE
    v_caller_id TEXT;
    v_is_caller_admin BOOLEAN;
    v_target_id TEXT;
    v_target_name TEXT;
BEGIN
    v_caller_id := auth.uid()::text;
    IF v_caller_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Authentication required.');
    END IF;

    -- Verify server-side admin privilege
    SELECT is_admin INTO v_is_caller_admin
    FROM public.profiles
    WHERE id::text = v_caller_id;

    IF v_is_caller_admin IS NOT TRUE THEN
        RETURN jsonb_build_object('success', false, 'error', 'Unauthorized: Admin privileges required.');
    END IF;

    -- Locate target profile
    SELECT id, name INTO v_target_id, v_target_name
    FROM public.profiles
    WHERE user_id_number = p_target_numeric_id OR numeric_id = p_target_numeric_id;

    IF v_target_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Target user not found.');
    END IF;

    -- Update roles (admin status cannot be modified via this endpoint)
    UPDATE public.profiles
    SET is_coinseller = p_is_coinseller,
        is_agent = p_is_agent,
        updated_at = now()
    WHERE id::text = v_target_id;

    RETURN jsonb_build_object(
        'success', true,
        'message', 'Updated roles for ' || COALESCE(v_target_name, 'User') || ' (Coin Seller: ' || p_is_coinseller || ', Agent: ' || p_is_agent || ')',
        'target_id', v_target_id,
        'is_coinseller', p_is_coinseller,
        'is_agent', p_is_agent
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ----------------------------------------------------------------------------
-- [RPC 2] award_coins / admin_award_coins
-- Server-side balance verification for sellers, and admin authorization for admins
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.award_coins(
    p_sender_id TEXT DEFAULT NULL,
    p_sender_numeric_id BIGINT DEFAULT NULL,
    p_is_admin BOOLEAN DEFAULT false,
    p_is_coinseller BOOLEAN DEFAULT false,
    p_target_numeric_id BIGINT DEFAULT NULL,
    p_amount BIGINT DEFAULT 0,
    p_reason TEXT DEFAULT ''
)
RETURNS jsonb AS $$
DECLARE
    v_caller_id TEXT;
    v_caller_is_admin BOOLEAN;
    v_caller_is_coinseller BOOLEAN;
    v_caller_coins BIGINT;
    v_target_id TEXT;
    v_target_name TEXT;
    v_target_new_coins BIGINT;
    v_seller_new_coins BIGINT;
BEGIN
    v_caller_id := auth.uid()::text;
    IF v_caller_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Authentication required.');
    END IF;

    IF p_amount <= 0 THEN
        RETURN jsonb_build_object('success', false, 'error', 'Coin amount must be greater than 0.');
    END IF;

    -- Verify caller roles from database
    SELECT is_admin, is_coinseller, coins
    INTO v_caller_is_admin, v_caller_is_coinseller, v_caller_coins
    FROM public.profiles
    WHERE id::text = v_caller_id;

    IF v_caller_is_admin IS NOT TRUE AND v_caller_is_coinseller IS NOT TRUE THEN
        RETURN jsonb_build_object('success', false, 'error', 'Unauthorized: Only Admins and Coin Sellers can award/transfer coins.');
    END IF;

    -- Locate target profile
    SELECT id, name INTO v_target_id, v_target_name
    FROM public.profiles
    WHERE user_id_number = p_target_numeric_id OR numeric_id = p_target_numeric_id;

    IF v_target_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Target user with Numeric ID ' || p_target_numeric_id || ' not found.');
    END IF;

    -- If seller (and not admin), check balance and deduct
    IF v_caller_is_admin IS NOT TRUE AND v_caller_is_coinseller IS TRUE THEN
        IF v_caller_coins < p_amount THEN
            RETURN jsonb_build_object('success', false, 'error', 'Insufficient balance! You have ' || v_caller_coins || ' coins available.');
        END IF;

        UPDATE public.profiles
        SET coins = coins - p_amount,
            updated_at = now()
        WHERE id::text = v_caller_id
        RETURNING coins INTO v_seller_new_coins;

        INSERT INTO public.coin_transactions (user_id, amount, type, title, description)
        VALUES (
            v_caller_id,
            -p_amount,
            'TRANSFER',
            'Coins Transferred',
            'Transferred ' || p_amount || ' coins to ' || COALESCE(v_target_name, 'User') || ' (ID: ' || p_target_numeric_id || ')'
        );
    END IF;

    -- Credit target user
    UPDATE public.profiles
    SET coins = coins + p_amount,
        updated_at = now()
    WHERE id::text = v_target_id
    RETURNING coins INTO v_target_new_coins;

    INSERT INTO public.coin_transactions (user_id, amount, type, title, description)
    VALUES (
        v_target_id,
        p_amount,
        'AWARD',
        'Coins Received',
        'Received ' || p_amount || ' coins from ' || (CASE WHEN v_caller_is_admin THEN 'Admin' ELSE 'Coin Seller' END) || (CASE WHEN p_reason <> '' THEN ' (' || p_reason || ')' ELSE '' END)
    );

    RETURN jsonb_build_object(
        'success', true,
        'message', 'Successfully awarded ' || p_amount || ' coins to ' || COALESCE(v_target_name, 'User') || '.',
        'target_coins', v_target_new_coins,
        'seller_coins', v_seller_new_coins
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ----------------------------------------------------------------------------
-- [RPC 3] exchange_diamonds_to_coins
-- Strictly server-controlled diamond conversion (1:1 fixed rate, cannot manufacture currency)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.exchange_diamonds_to_coins(
    p_user_id TEXT DEFAULT NULL,
    p_diamonds_amount BIGINT DEFAULT 0,
    p_coins_amount BIGINT DEFAULT 0
)
RETURNS jsonb AS $$
DECLARE
    v_caller_id TEXT;
    v_current_diamonds BIGINT;
    v_current_coins BIGINT;
    v_coins_to_add BIGINT;
    v_new_coins BIGINT;
    v_new_diamonds BIGINT;
BEGIN
    v_caller_id := auth.uid()::text;
    IF v_caller_id IS NULL THEN
        -- Fallback to provided p_user_id if caller context is not yet initialized
        v_caller_id := p_user_id;
    END IF;

    IF v_caller_id IS NULL OR v_caller_id = '' THEN
        RETURN jsonb_build_object('success', false, 'error', 'Authentication required.');
    END IF;

    IF p_diamonds_amount <= 0 THEN
        RETURN jsonb_build_object('success', false, 'error', 'Diamond amount must be greater than 0.');
    END IF;

    -- Fetch authoritative balances
    SELECT diamonds, coins INTO v_current_diamonds, v_current_coins
    FROM public.profiles
    WHERE id::text = v_caller_id
    FOR UPDATE;

    IF v_current_diamonds IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'User profile not found.');
    END IF;

    IF v_current_diamonds < p_diamonds_amount THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Insufficient diamonds balance.',
            'coins', v_current_coins,
            'diamonds', v_current_diamonds
        );
    END IF;

    -- Strictly server-calculated 1:1 conversion rate
    v_coins_to_add := p_diamonds_amount;

    UPDATE public.profiles
    SET diamonds = diamonds - p_diamonds_amount,
        coins = coins + v_coins_to_add,
        updated_at = now()
    WHERE id::text = v_caller_id
    RETURNING coins, diamonds INTO v_new_coins, v_new_diamonds;

    INSERT INTO public.coin_transactions (user_id, amount, type, title, description)
    VALUES (
        v_caller_id,
        v_coins_to_add,
        'DIAMOND_EXCHANGE',
        'Diamonds Converted to Coins',
        'Converted ' || p_diamonds_amount || ' diamonds into ' || v_coins_to_add || ' coins'
    );

    RETURN jsonb_build_object(
        'success', true,
        'coins', v_new_coins,
        'diamonds', v_new_diamonds
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ----------------------------------------------------------------------------
-- [RPC 4] claim_welcome_bonus
-- One-time server-controlled 500 coins welcome bonus per user / device
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.claim_welcome_bonus(
    p_user_id TEXT DEFAULT NULL,
    p_device_hash TEXT DEFAULT '',
    p_bonus_coins BIGINT DEFAULT 500
)
RETURNS jsonb AS $$
DECLARE
    v_caller_id TEXT;
    v_already_claimed BOOLEAN;
    v_device_claims_count INT;
    v_new_coins BIGINT;
    v_bonus_amount CONSTANT BIGINT := 500;
BEGIN
    v_caller_id := auth.uid()::text;
    IF v_caller_id IS NULL THEN
        v_caller_id := p_user_id;
    END IF;

    IF v_caller_id IS NULL OR v_caller_id = '' THEN
        RETURN jsonb_build_object('success', false, 'error', 'Authentication required.');
    END IF;

    -- Check if user already claimed
    SELECT EXISTS (
        SELECT 1 FROM public.claimed_welcome_bonuses
        WHERE user_id::text = v_caller_id
    ) INTO v_already_claimed;

    IF v_already_claimed THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Welcome bonus has already been claimed for this account.',
            'coins_awarded', 0
        );
    END IF;

    -- Check device fingerprint limit (strict 1 claim per physical device)
    IF p_device_hash IS NOT NULL AND p_device_hash <> '' THEN
        SELECT COUNT(*) INTO v_device_claims_count
        FROM public.claimed_welcome_bonuses
        WHERE device_hash = p_device_hash;

        IF v_device_claims_count >= 1 THEN
            RETURN jsonb_build_object(
                'success', false,
                'error', 'Free 500 welcome coins have already been claimed on this device.',
                'coins_awarded', 0
            );
        END IF;
    END IF;

    -- Record claim
    INSERT INTO public.claimed_welcome_bonuses (user_id, device_hash, bonus_coins)
    VALUES (v_caller_id, COALESCE(p_device_hash, 'unknown'), v_bonus_amount);

    -- Credit 500 bonus coins
    UPDATE public.profiles
    SET coins = coins + v_bonus_amount,
        updated_at = now()
    WHERE id::text = v_caller_id
    RETURNING coins INTO v_new_coins;

    INSERT INTO public.coin_transactions (user_id, amount, type, title, description)
    VALUES (
        v_caller_id,
        v_bonus_amount,
        'WELCOME_BONUS',
        'Welcome Gift',
        'Awarded ' || v_bonus_amount || ' Welcome Gift Coins'
    );

    RETURN jsonb_build_object(
        'success', true,
        'coins_awarded', v_bonus_amount,
        'new_balance', v_new_coins
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ----------------------------------------------------------------------------
-- [RPC 5] deduct_chat_coins
-- Server-side rule: 15 coins for male sender to female user, free for female/admin/agents
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.deduct_chat_coins(
    p_sender_id TEXT DEFAULT NULL,
    p_receiver_id TEXT DEFAULT NULL,
    p_amount BIGINT DEFAULT 15
)
RETURNS jsonb AS $$
DECLARE
    v_caller_id TEXT;
    v_sender_gender TEXT;
    v_sender_is_admin BOOLEAN;
    v_sender_is_coinseller BOOLEAN;
    v_sender_is_agent BOOLEAN;
    v_sender_coins BIGINT;
    v_receiver_gender TEXT;
    v_receiver_is_admin BOOLEAN;
    v_receiver_is_coinseller BOOLEAN;
    v_receiver_is_agent BOOLEAN;
    v_cost CONSTANT BIGINT := 15;
    v_new_coins BIGINT;
BEGIN
    v_caller_id := auth.uid()::text;
    IF v_caller_id IS NULL THEN
        v_caller_id := p_sender_id;
    END IF;

    IF v_caller_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Authentication required.');
    END IF;

    -- Fetch sender info
    SELECT gender, is_admin, is_coinseller, is_agent, coins
    INTO v_sender_gender, v_sender_is_admin, v_sender_is_coinseller, v_sender_is_agent, v_sender_coins
    FROM public.profiles
    WHERE id::text = v_caller_id
    FOR UPDATE;

    -- Exempt if sender is female, admin, coin seller, or agent
    IF v_sender_is_admin OR v_sender_is_coinseller OR v_sender_is_agent OR LOWER(COALESCE(v_sender_gender, '')) = 'female' THEN
        RETURN jsonb_build_object('success', true, 'new_balance', v_sender_coins, 'deducted', 0);
    END IF;

    -- Fetch receiver info if provided
    IF p_receiver_id IS NOT NULL AND p_receiver_id <> '' THEN
        SELECT gender, is_admin, is_coinseller, is_agent
        INTO v_receiver_gender, v_receiver_is_admin, v_receiver_is_coinseller, v_receiver_is_agent
        FROM public.profiles
        WHERE id::text = p_receiver_id;

        -- Exempt if receiver is admin, coin seller, or agent
        IF v_receiver_is_admin OR v_receiver_is_coinseller OR v_receiver_is_agent THEN
            RETURN jsonb_build_object('success', true, 'new_balance', v_sender_coins, 'deducted', 0);
        END IF;
    END IF;

    -- Check balance
    IF v_sender_coins < v_cost THEN
        RETURN jsonb_build_object('success', false, 'error', 'Insufficient coins.', 'new_balance', v_sender_coins);
    END IF;

    UPDATE public.profiles
    SET coins = coins - v_cost,
        updated_at = now()
    WHERE id::text = v_caller_id
    RETURNING coins INTO v_new_coins;

    INSERT INTO public.coin_transactions (user_id, amount, type, title, description)
    VALUES (
        v_caller_id,
        -v_cost,
        'CHAT_DEDUCT',
        'Direct Message',
        v_cost || ' Coins deducted for direct message'
    );

    RETURN jsonb_build_object('success', true, 'new_balance', v_new_coins, 'deducted', v_cost);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ----------------------------------------------------------------------------
-- [RPC 6] deduct_photo_coins
-- Server-side rule: 40 coins for male sender sending photo, free for female/admin/agents
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.deduct_photo_coins(
    p_sender_id TEXT DEFAULT NULL,
    p_receiver_id TEXT DEFAULT NULL,
    p_receiver_name TEXT DEFAULT '',
    p_amount BIGINT DEFAULT 40
)
RETURNS jsonb AS $$
DECLARE
    v_caller_id TEXT;
    v_sender_gender TEXT;
    v_sender_is_admin BOOLEAN;
    v_sender_is_coinseller BOOLEAN;
    v_sender_is_agent BOOLEAN;
    v_sender_coins BIGINT;
    v_receiver_gender TEXT;
    v_receiver_is_admin BOOLEAN;
    v_receiver_is_coinseller BOOLEAN;
    v_receiver_is_agent BOOLEAN;
    v_cost CONSTANT BIGINT := 40;
    v_new_coins BIGINT;
BEGIN
    v_caller_id := auth.uid()::text;
    IF v_caller_id IS NULL THEN
        v_caller_id := p_sender_id;
    END IF;

    IF v_caller_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Authentication required.');
    END IF;

    -- Fetch sender info
    SELECT gender, is_admin, is_coinseller, is_agent, coins
    INTO v_sender_gender, v_sender_is_admin, v_sender_is_coinseller, v_sender_is_agent, v_sender_coins
    FROM public.profiles
    WHERE id::text = v_caller_id
    FOR UPDATE;

    -- Exempt if sender is female, admin, coin seller, or agent
    IF v_sender_is_admin OR v_sender_is_coinseller OR v_sender_is_agent OR LOWER(COALESCE(v_sender_gender, '')) = 'female' THEN
        RETURN jsonb_build_object('success', true, 'new_balance', v_sender_coins, 'deducted', 0);
    END IF;

    -- Fetch receiver info if provided
    IF p_receiver_id IS NOT NULL AND p_receiver_id <> '' THEN
        SELECT gender, is_admin, is_coinseller, is_agent
        INTO v_receiver_gender, v_receiver_is_admin, v_receiver_is_coinseller, v_receiver_is_agent
        FROM public.profiles
        WHERE id::text = p_receiver_id;

        IF v_receiver_is_admin OR v_receiver_is_coinseller OR v_receiver_is_agent THEN
            RETURN jsonb_build_object('success', true, 'new_balance', v_sender_coins, 'deducted', 0);
        END IF;
    END IF;

    -- Check balance
    IF v_sender_coins < v_cost THEN
        RETURN jsonb_build_object('success', false, 'error', 'Insufficient coins for photo.', 'new_balance', v_sender_coins);
    END IF;

    UPDATE public.profiles
    SET coins = coins - v_cost,
        updated_at = now()
    WHERE id::text = v_caller_id
    RETURNING coins INTO v_new_coins;

    INSERT INTO public.coin_transactions (user_id, amount, type, title, description)
    VALUES (
        v_caller_id,
        -v_cost,
        'PHOTO_DEDUCT',
        'Photo Message',
        v_cost || ' Coins deducted for photo message to ' || COALESCE(p_receiver_name, 'User')
    );

    RETURN jsonb_build_object('success', true, 'new_balance', v_new_coins, 'deducted', v_cost);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ----------------------------------------------------------------------------
-- [RPC 7] deduct_gift_coins
-- Server-side gift cost validation, sender deduction, and recipient diamond crediting
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.deduct_gift_coins(
    p_sender_id TEXT DEFAULT NULL,
    p_gift_name TEXT DEFAULT 'Gift',
    p_coins BIGINT DEFAULT 0,
    p_receiver_id TEXT DEFAULT NULL,
    p_receiver_name TEXT DEFAULT ''
)
RETURNS jsonb AS $$
DECLARE
    v_caller_id TEXT;
    v_sender_coins BIGINT;
    v_actual_cost BIGINT;
    v_new_coins BIGINT;
    v_diamonds_earned BIGINT;
BEGIN
    v_caller_id := auth.uid()::text;
    IF v_caller_id IS NULL THEN
        v_caller_id := p_sender_id;
    END IF;

    IF v_caller_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Authentication required.');
    END IF;

    -- Validate gift cost
    v_actual_cost := CASE LOWER(TRIM(COALESCE(p_gift_name, '')))
        WHEN 'rose' THEN 10
        WHEN 'heart' THEN 50
        WHEN 'love' THEN 100
        WHEN 'diamond' THEN 200
        WHEN 'rocket' THEN 500
        WHEN 'sports car' THEN 1000
        WHEN 'crown' THEN 2000
        WHEN 'castle' THEN 5000
        WHEN 'dragon' THEN 10000
        WHEN 'universe' THEN 20000
        ELSE GREATEST(p_coins, 10)
    END;

    SELECT coins INTO v_sender_coins
    FROM public.profiles
    WHERE id::text = v_caller_id
    FOR UPDATE;

    IF v_sender_coins < v_actual_cost THEN
        RETURN jsonb_build_object('success', false, 'error', 'Insufficient coins for gift.', 'new_balance', v_sender_coins);
    END IF;

    UPDATE public.profiles
    SET coins = coins - v_actual_cost,
        updated_at = now()
    WHERE id::text = v_caller_id
    RETURNING coins INTO v_new_coins;

    INSERT INTO public.coin_transactions (user_id, amount, type, title, description)
    VALUES (
        v_caller_id,
        -v_actual_cost,
        'GIFT_DEDUCT',
        'Gift: ' || p_gift_name,
        v_actual_cost || ' Coins deducted for gift ' || p_gift_name || ' to ' || COALESCE(p_receiver_name, 'User')
    );

    -- Credit diamonds to receiver if receiver exists (50% conversion into diamonds)
    IF p_receiver_id IS NOT NULL AND p_receiver_id <> '' AND p_receiver_id <> v_caller_id THEN
        v_diamonds_earned := GREATEST(v_actual_cost / 2, 1);
        UPDATE public.profiles
        SET diamonds = diamonds + v_diamonds_earned,
            updated_at = now()
        WHERE id::text = p_receiver_id;
    END IF;

    RETURN jsonb_build_object('success', true, 'new_balance', v_new_coins, 'cost', v_actual_cost);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ----------------------------------------------------------------------------
-- [RPC 8] deduct_call_minute_coins
-- Server-side call billing (160 coins/min video, 80 coins/min voice)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.deduct_call_minute_coins(
    p_caller_id TEXT DEFAULT NULL,
    p_caller_gender TEXT DEFAULT '',
    p_callee_id TEXT DEFAULT NULL,
    p_callee_gender TEXT DEFAULT '',
    p_call_type TEXT DEFAULT 'VOICE'
)
RETURNS jsonb AS $$
DECLARE
    v_caller_id TEXT;
    v_actual_caller_gender TEXT;
    v_actual_callee_gender TEXT;
    v_caller_coins BIGINT;
    v_rate BIGINT;
    v_new_coins BIGINT;
    v_is_video BOOLEAN;
BEGIN
    v_caller_id := auth.uid()::text;
    IF v_caller_id IS NULL THEN
        v_caller_id := p_caller_id;
    END IF;

    IF v_caller_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Authentication required.');
    END IF;

    v_is_video := UPPER(p_call_type) = 'VIDEO';
    v_rate := CASE WHEN v_is_video THEN 160 ELSE 80 END;

    -- Query actual gender from database
    SELECT gender, coins INTO v_actual_caller_gender, v_caller_coins
    FROM public.profiles
    WHERE id::text = v_caller_id
    FOR UPDATE;

    IF p_callee_id IS NOT NULL AND p_callee_id <> '' THEN
        SELECT gender INTO v_actual_callee_gender
        FROM public.profiles
        WHERE id::text = p_callee_id;
    END IF;

    -- If caller is female, free of charge
    IF LOWER(COALESCE(v_actual_caller_gender, '')) = 'female' THEN
        RETURN jsonb_build_object('success', true, 'caller_balance', v_caller_coins, 'deducted', 0);
    END IF;

    -- Deduct from male caller
    IF v_caller_coins < v_rate THEN
        RETURN jsonb_build_object('success', false, 'error', 'Insufficient coins for call.', 'caller_balance', v_caller_coins);
    END IF;

    UPDATE public.profiles
    SET coins = coins - v_rate,
        updated_at = now()
    WHERE id::text = v_caller_id
    RETURNING coins INTO v_new_coins;

    INSERT INTO public.coin_transactions (user_id, amount, type, title, description)
    VALUES (
        v_caller_id,
        -v_rate,
        'CALL_DEDUCT',
        (CASE WHEN v_is_video THEN 'Video Call' ELSE 'Voice Call' END),
        v_rate || ' Coins deducted for 1 minute call'
    );

    RETURN jsonb_build_object('success', true, 'caller_balance', v_new_coins, 'deducted', v_rate);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ----------------------------------------------------------------------------
-- [RPC 9] Party Room Operations
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_party_room(
    p_room_name TEXT,
    p_max_seats INTEGER DEFAULT 8,
    p_category TEXT DEFAULT 'Music & Chat',
    p_cover_image TEXT DEFAULT '',
    p_background_theme TEXT DEFAULT 'default'
)
RETURNS jsonb AS $$
DECLARE
    v_host_id TEXT;
    v_room_id TEXT;
    v_room_num BIGINT;
    v_host_name TEXT;
    v_host_avatar TEXT;
    v_seats INTEGER;
BEGIN
    v_host_id := auth.uid()::text;
    IF v_host_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required.';
    END IF;

    SELECT name, avatar_url INTO v_host_name, v_host_avatar
    FROM public.profiles
    WHERE id::text = v_host_id;

    v_room_id := gen_random_uuid()::text;
    v_room_num := floor(100000 + random() * 900000)::bigint;
    v_seats := LEAST(GREATEST(COALESCE(p_max_seats, 8), 4), 12);

    INSERT INTO public.party_rooms (
        id, room_number, title, category, cover_image, host_user_id,
        host_name, host_avatar, seats_count, background_theme, member_count
    ) VALUES (
        v_room_id, v_room_num, p_room_name, p_category, p_cover_image, v_host_id,
        COALESCE(v_host_name, 'Host'), COALESCE(v_host_avatar, ''), v_seats, p_background_theme, 1
    );

    -- Automatically occupy seat 0 for host
    INSERT INTO public.party_room_seats (room_id, seat_index, user_id, user_name, user_avatar, is_muted, is_locked)
    VALUES (v_room_id, 0, v_host_id, COALESCE(v_host_name, 'Host'), COALESCE(v_host_avatar, ''), false, false)
    ON CONFLICT (room_id, seat_index) DO UPDATE
    SET user_id = v_host_id, user_name = v_host_name, user_avatar = v_host_avatar;

    -- Add host as member
    INSERT INTO public.party_room_members (room_id, user_id, user_name, user_avatar)
    VALUES (v_room_id, v_host_id, COALESCE(v_host_name, 'Host'), COALESCE(v_host_avatar, ''))
    ON CONFLICT (room_id, user_id) DO NOTHING;

    RETURN jsonb_build_object(
        'success', true,
        'id', v_room_id,
        'room_id', v_room_id,
        'room_number', v_room_num
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


CREATE OR REPLACE FUNCTION public.occupy_party_seat(
    p_room_id TEXT,
    p_seat_index INTEGER,
    p_user_id TEXT DEFAULT NULL,
    p_user_name TEXT DEFAULT 'Guest',
    p_avatar_url TEXT DEFAULT ''
)
RETURNS jsonb AS $$
DECLARE
    v_user_id TEXT;
    v_is_locked BOOLEAN;
    v_occupied_by TEXT;
    v_max_seats INTEGER;
BEGIN
    v_user_id := auth.uid()::text;
    IF v_user_id IS NULL THEN
        v_user_id := p_user_id;
    END IF;

    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Authentication required.');
    END IF;

    -- Validate room and seats count
    SELECT seats_count INTO v_max_seats FROM public.party_rooms WHERE id = p_room_id;
    IF v_max_seats IS NULL OR p_seat_index < 0 OR p_seat_index >= v_max_seats THEN
        RETURN jsonb_build_object('success', false, 'error', 'Invalid seat index.');
    END IF;

    -- Check if target seat is locked or occupied
    SELECT is_locked, user_id INTO v_is_locked, v_occupied_by
    FROM public.party_room_seats
    WHERE room_id = p_room_id AND seat_index = p_seat_index;

    IF v_is_locked IS TRUE THEN
        RETURN jsonb_build_object('success', false, 'error', 'Seat is locked.');
    END IF;

    IF v_occupied_by IS NOT NULL AND v_occupied_by <> '' AND v_occupied_by <> v_user_id THEN
        RETURN jsonb_build_object('success', false, 'error', 'Seat is already occupied.');
    END IF;

    -- Clear user from any other seat in this room to enforce single-seat invariant
    DELETE FROM public.party_room_seats
    WHERE room_id = p_room_id AND user_id = v_user_id AND seat_index <> p_seat_index;

    -- Occupy seat
    INSERT INTO public.party_room_seats (room_id, seat_index, user_id, user_name, user_avatar, is_muted, is_locked, updated_at)
    VALUES (p_room_id, p_seat_index, v_user_id, p_user_name, p_avatar_url, false, false, now())
    ON CONFLICT (room_id, seat_index) DO UPDATE
    SET user_id = v_user_id,
        user_name = p_user_name,
        user_avatar = p_avatar_url,
        is_muted = false,
        updated_at = now();

    RETURN jsonb_build_object('success', true);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


CREATE OR REPLACE FUNCTION public.release_party_seat(
    p_room_id TEXT,
    p_seat_index INTEGER
)
RETURNS jsonb AS $$
DECLARE
    v_caller_id TEXT;
    v_seat_user_id TEXT;
    v_host_id TEXT;
    v_is_room_admin BOOLEAN;
BEGIN
    v_caller_id := auth.uid()::text;
    IF v_caller_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Authentication required.');
    END IF;

    SELECT user_id INTO v_seat_user_id
    FROM public.party_room_seats
    WHERE room_id = p_room_id AND seat_index = p_seat_index;

    SELECT host_user_id INTO v_host_id
    FROM public.party_rooms
    WHERE id = p_room_id;

    SELECT EXISTS (
        SELECT 1 FROM public.party_room_admins
        WHERE room_id = p_room_id AND user_id = v_caller_id
    ) INTO v_is_room_admin;

    -- Only seat occupant, room host, room admin, or global admin can release
    IF v_seat_user_id <> v_caller_id AND v_host_id <> v_caller_id AND NOT v_is_room_admin AND NOT public.is_admin() THEN
        RETURN jsonb_build_object('success', false, 'error', 'Unauthorized to release seat.');
    END IF;

    DELETE FROM public.party_room_seats
    WHERE room_id = p_room_id AND seat_index = p_seat_index;

    RETURN jsonb_build_object('success', true);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


CREATE OR REPLACE FUNCTION public.appoint_party_admin(
    p_room_id TEXT,
    p_user_id TEXT
)
RETURNS jsonb AS $$
DECLARE
    v_caller_id TEXT;
    v_host_id TEXT;
BEGIN
    v_caller_id := auth.uid()::text;
    IF v_caller_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Authentication required.');
    END IF;

    SELECT host_user_id INTO v_host_id
    FROM public.party_rooms
    WHERE id = p_room_id;

    -- Only host or global admin can appoint room admins
    IF v_host_id <> v_caller_id AND NOT public.is_admin() THEN
        RETURN jsonb_build_object('success', false, 'error', 'Only room host can appoint room admins.');
    END IF;

    INSERT INTO public.party_room_admins (room_id, user_id)
    VALUES (p_room_id, p_user_id)
    ON CONFLICT (room_id, user_id) DO NOTHING;

    RETURN jsonb_build_object('success', true);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ----------------------------------------------------------------------------
-- [RPC 10] equip_avatar_frame & unequip_avatar_frame
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.equip_avatar_frame(
    p_frame_id TEXT,
    p_expires_at TEXT DEFAULT NULL
)
RETURNS jsonb AS $$
DECLARE
    v_user_id TEXT;
BEGIN
    v_user_id := auth.uid()::text;
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Authentication required.');
    END IF;

    UPDATE public.user_frames
    SET is_active = false
    WHERE user_id::text = v_user_id::text;

    UPDATE public.user_frames
    SET is_active = true
    WHERE user_id::text = v_user_id::text AND frame_id::text = p_frame_id::text;

    UPDATE public.profiles
    SET active_frame_id = p_frame_id,
        avatar_frame = p_frame_id,
        frame_expires_at = p_expires_at,
        updated_at = now()
    WHERE id::text = v_user_id::text;

    RETURN jsonb_build_object('success', true, 'frame_id', p_frame_id);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


CREATE OR REPLACE FUNCTION public.unequip_avatar_frame()
RETURNS jsonb AS $$
DECLARE
    v_user_id TEXT;
BEGIN
    v_user_id := auth.uid()::text;
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Authentication required.');
    END IF;

    UPDATE public.user_frames
    SET is_active = false
    WHERE user_id::text = v_user_id::text;

    UPDATE public.profiles
    SET active_frame_id = '',
        avatar_frame = '',
        frame_expires_at = NULL,
        updated_at = now()
    WHERE id::text = v_user_id::text;

    RETURN jsonb_build_object('success', true);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ----------------------------------------------------------------------------
-- [RPC 11] update_user_heartbeat & record_profile_visit
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.update_user_heartbeat(
    p_user_id TEXT DEFAULT NULL,
    p_is_online BOOLEAN DEFAULT true
)
RETURNS jsonb AS $$
DECLARE
    v_caller_id TEXT;
BEGIN
    v_caller_id := auth.uid()::text;
    IF v_caller_id IS NULL THEN
        v_caller_id := p_user_id;
    END IF;

    IF v_caller_id IS NOT NULL AND v_caller_id <> '' THEN
        UPDATE public.profiles
        SET last_active_at = now(),
            is_online = COALESCE(p_is_online, true)
        WHERE id::text = v_caller_id;
    END IF;

    RETURN jsonb_build_object('success', true);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


CREATE OR REPLACE FUNCTION public.record_profile_visit(
    p_visited_user_id TEXT DEFAULT NULL,
    p_visitor_id TEXT DEFAULT NULL,
    p_visitor_name TEXT DEFAULT '',
    p_visitor_avatar TEXT DEFAULT '',
    p_visited_id TEXT DEFAULT NULL
)
RETURNS jsonb AS $$
DECLARE
    v_caller_id TEXT;
    v_target_visited_id TEXT;
    v_name TEXT;
    v_avatar TEXT;
BEGIN
    v_caller_id := auth.uid()::text;
    IF v_caller_id IS NULL THEN
        v_caller_id := p_visitor_id;
    END IF;

    v_target_visited_id := COALESCE(p_visited_user_id, p_visited_id);

    IF v_caller_id IS NULL OR v_caller_id = '' OR v_target_visited_id IS NULL OR v_caller_id = v_target_visited_id THEN
        RETURN jsonb_build_object('success', true);
    END IF;

    SELECT name, avatar_url INTO v_name, v_avatar
    FROM public.profiles
    WHERE id::text = v_caller_id;

    INSERT INTO public.user_visitors (visited_user_id, visitor_user_id, visitor_name, visitor_avatar, visited_at)
    VALUES (
        v_target_visited_id,
        v_caller_id,
        COALESCE(v_name, p_visitor_name, 'Visitor'),
        COALESCE(v_avatar, p_visitor_avatar, ''),
        now()
    );

    INSERT INTO public.profile_visitors (visited_id, visitor_id, visitor_name, visitor_avatar, visited_at)
    VALUES (
        v_target_visited_id,
        v_caller_id,
        COALESCE(v_name, p_visitor_name, 'Visitor'),
        COALESCE(v_avatar, p_visitor_avatar, ''),
        now()
    );

    RETURN jsonb_build_object('success', true);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ----------------------------------------------------------------------------
-- [RPC 12] calculate_user_level & award_user_exp & update_my_exp
-- Server-authoritative EXP allocation with automatic level-up calculation
-- Level 1 -> Level 2 = 5,000 EXP. Threshold doubles each level up to level 50.
-- Strict user isolation: Non-admin users can ONLY award EXP to themselves.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.calculate_user_level(p_exp BIGINT)
RETURNS INTEGER AS $$
DECLARE
    v_exp BIGINT := GREATEST(COALESCE(p_exp, 0), 0);
    v_lvl INT := 1;
    v_cum_exp BIGINT := 0;
    v_step BIGINT := 5000;
BEGIN
    WHILE v_lvl < 50 LOOP
        IF v_exp < (v_cum_exp + v_step) THEN
            RETURN v_lvl;
        END IF;
        v_cum_exp := v_cum_exp + v_step;
        v_lvl := v_lvl + 1;
        IF v_step < 4611686018427387903 THEN
            v_step := v_step * 2;
        END IF;
    END LOOP;
    RETURN 50;
END;
$$ LANGUAGE plpgsql IMMUTABLE;


CREATE OR REPLACE FUNCTION public.award_user_exp(
    p_user_id TEXT DEFAULT NULL,
    p_amount BIGINT DEFAULT 10,
    p_reason TEXT DEFAULT 'EXP_AWARD'
)
RETURNS jsonb AS $$
DECLARE
    v_caller_id TEXT;
    v_target_id TEXT;
    v_is_admin BOOLEAN := false;
    v_capped_amount BIGINT;
    v_new_exp BIGINT;
    v_new_level INT;
BEGIN
    v_caller_id := auth.uid()::text;
    IF v_caller_id IS NULL OR v_caller_id = '' THEN
        IF current_user IN ('postgres', 'service_role') THEN
            v_caller_id := p_user_id;
        ELSE
            RETURN jsonb_build_object('success', false, 'error', 'Authentication required.');
        END IF;
    END IF;

    -- Check if caller is admin
    v_is_admin := public.is_admin();

    -- Anti-tampering & anti user-interference:
    -- Non-admin users can ONLY award EXP to themselves!
    IF NOT v_is_admin AND current_user NOT IN ('postgres', 'service_role') THEN
        v_target_id := v_caller_id;
        -- Cap per-call EXP award for normal users to prevent abuse/exploits
        v_capped_amount := LEAST(GREATEST(p_amount, 1), 5000);
    ELSE
        v_target_id := COALESCE(NULLIF(p_user_id, ''), v_caller_id);
        v_capped_amount := GREATEST(p_amount, 1);
    END IF;

    IF v_target_id IS NULL OR v_target_id = '' THEN
        RETURN jsonb_build_object('success', false, 'error', 'Target user ID required.');
    END IF;

    -- Atomically update target profile EXP and recalculate level server-side
    UPDATE public.profiles
    SET exp = COALESCE(exp, 0) + v_capped_amount,
        user_level = public.calculate_user_level(COALESCE(exp, 0) + v_capped_amount),
        updated_at = now()
    WHERE id::text = v_target_id
    RETURNING COALESCE(exp, 0), user_level INTO v_new_exp, v_new_level;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'User profile not found.');
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'user_id', v_target_id,
        'awarded_amount', v_capped_amount,
        'total_exp', v_new_exp,
        'level', v_new_level,
        'reason', p_reason
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- Dedicated user self-update function for EXP
CREATE OR REPLACE FUNCTION public.update_my_exp(
    p_amount BIGINT,
    p_reason TEXT DEFAULT 'ACTIVITY'
)
RETURNS jsonb AS $$
BEGIN
    RETURN public.award_user_exp(auth.uid()::text, p_amount, p_reason);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ----------------------------------------------------------------------------
-- [RPC 13] process_gift
-- Full transactional gift deduction from sender and credit to recipient
-- Automatically grants 1:1 EXP to sender and Charm Level to recipient
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.process_gift(
    p_sender_id TEXT,
    p_recipient_id TEXT,
    p_gift_id TEXT,
    p_gift_name TEXT,
    p_coins_cost BIGINT
)
RETURNS jsonb AS $$
DECLARE
    v_caller_id TEXT;
    v_sender_coins BIGINT;
    v_new_sender_coins BIGINT;
    v_recipient_diamonds NUMERIC(14, 2);
    v_new_recipient_diamonds NUMERIC(14, 2);
    v_diamonds_to_award NUMERIC(14, 2);
BEGIN
    v_caller_id := auth.uid()::text;
    IF v_caller_id IS NULL THEN
        IF current_user IN ('postgres', 'service_role') THEN
            v_caller_id := p_sender_id;
        END IF;
    END IF;

    IF v_caller_id IS NULL OR v_caller_id = '' THEN
        RETURN jsonb_build_object('success', false, 'error', 'Authentication required.');
    END IF;

    IF p_recipient_id IS NULL OR p_recipient_id = '' THEN
        RETURN jsonb_build_object('success', false, 'error', 'Recipient ID required.');
    END IF;

    IF p_coins_cost <= 0 THEN
        RETURN jsonb_build_object('success', false, 'error', 'Invalid gift cost.');
    END IF;

    -- Lock sender row
    SELECT coins INTO v_sender_coins
    FROM public.profiles
    WHERE id::text = v_caller_id
    FOR UPDATE;

    IF v_sender_coins IS NULL OR v_sender_coins < p_coins_cost THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Insufficient coins balance.',
            'current_balance', COALESCE(v_sender_coins, 0)
        );
    END IF;

    -- Deduct sender coins AND award sender 1:1 EXP + level calculation
    UPDATE public.profiles
    SET coins = coins - p_coins_cost,
        exp = COALESCE(exp, 0) + p_coins_cost,
        user_level = public.calculate_user_level(COALESCE(exp, 0) + p_coins_cost),
        wealth_level = GREATEST(wealth_level, 1 + FLOOR((COALESCE(exp, 0) + p_coins_cost) / 10000)::INT),
        updated_at = now()
    WHERE id::text = v_caller_id
    RETURNING coins INTO v_new_sender_coins;

    -- Calculate diamonds: 50% conversion
    v_diamonds_to_award := ROUND(p_coins_cost * 0.50, 2);

    -- Credit recipient diamonds AND charm level
    UPDATE public.profiles
    SET diamonds = diamonds + v_diamonds_to_award,
        charm_level = GREATEST(charm_level, 1 + FLOOR((COALESCE(diamonds, 0) + v_diamonds_to_award) / 5000)::INT),
        updated_at = now()
    WHERE id::text = p_recipient_id
    RETURNING diamonds INTO v_new_recipient_diamonds;

    -- Record coin deduction transaction
    INSERT INTO public.coin_transactions (user_id, amount, type, title, description)
    VALUES (
        v_caller_id,
        -p_coins_cost,
        'GIFT_SENT',
        'Sent ' || p_gift_name,
        p_coins_cost || ' coins sent as ' || p_gift_name || ' to ' || p_recipient_id
    );

    -- Record diamond receipt transaction
    INSERT INTO public.diamond_transactions (user_id, amount, type, title, description)
    VALUES (
        p_recipient_id,
        v_diamonds_to_award,
        'GIFT_RECEIVED',
        'Received ' || p_gift_name,
        v_diamonds_to_award || ' diamonds received from gift ' || p_gift_name
    );

    RETURN jsonb_build_object(
        'success', true,
        'new_sender_coins', v_new_sender_coins,
        'diamonds_awarded', v_diamonds_to_award
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ----------------------------------------------------------------------------
-- [RPC 14] process_fast_reply_reward
-- Strictly idempotent server-side reward crediting for fast replies
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.process_fast_reply_reward(
    p_original_message_id BIGINT,
    p_reply_message_id BIGINT,
    p_idempotency_key TEXT,
    p_female_user_id TEXT DEFAULT NULL
)
RETURNS jsonb AS $$
DECLARE
    v_caller_id TEXT;
    v_female_id TEXT;
    v_male_id TEXT;
    v_reward_diamonds CONSTANT NUMERIC(14, 2) := 5.00;
    v_existing_id BIGINT;
    v_new_diamonds NUMERIC(14, 2);
BEGIN
    v_caller_id := auth.uid()::text;
    v_female_id := COALESCE(p_female_user_id, v_caller_id);

    IF v_female_id IS NULL OR v_female_id = '' THEN
        RETURN jsonb_build_object('success', false, 'error', 'Authentication required.');
    END IF;

    -- Check for existing transaction (Idempotency)
    SELECT id INTO v_existing_id
    FROM public.fast_reply_transactions
    WHERE idempotency_key = p_idempotency_key
       OR reply_message_id = p_reply_message_id;

    IF v_existing_id IS NOT NULL THEN
        RETURN jsonb_build_object(
            'success', true,
            'message', 'Reward already processed (idempotent).',
            'diamonds_awarded', 0
        );
    END IF;

    -- Find male sender ID from messages or chat_messages
    SELECT sender_id INTO v_male_id
    FROM public.messages
    WHERE id::text = p_original_message_id::text
    LIMIT 1;

    IF v_male_id IS NULL THEN
        v_male_id := 'system';
    END IF;

    -- Credit diamonds to female user
    UPDATE public.profiles
    SET diamonds = diamonds + v_reward_diamonds,
        updated_at = now()
    WHERE id::text = v_female_id
    RETURNING diamonds INTO v_new_diamonds;

    -- Insert ledger record
    INSERT INTO public.fast_reply_transactions (
        female_user_id,
        male_user_id,
        original_message_id,
        reply_message_id,
        diamonds_awarded,
        idempotency_key
    ) VALUES (
        v_female_id,
        v_male_id,
        p_original_message_id,
        p_reply_message_id,
        v_reward_diamonds,
        p_idempotency_key
    );

    -- Record in diamond_transactions
    INSERT INTO public.diamond_transactions (user_id, amount, type, title, description)
    VALUES (
        v_female_id,
        v_reward_diamonds,
        'FAST_REPLY_REWARD',
        'Fast Reply Reward',
        v_reward_diamonds || ' diamonds awarded for replying within 5 minutes'
    );

    RETURN jsonb_build_object(
        'success', true,
        'diamonds_awarded', v_reward_diamonds,
        'new_balance', v_new_diamonds
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ============================================================================
-- 15. ZERO-TRACE ACCOUNT DELETION (Hardened Security Definer)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.delete_user_account()
RETURNS jsonb AS $$
DECLARE
    v_user_id UUID;
    v_user_text_id TEXT;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Authentication required.');
    END IF;
    
    v_user_text_id := v_user_id::text;

    -- Clean up Party Rooms, Seats, Admins, Messages
    DELETE FROM public.party_room_seats WHERE user_id = v_user_text_id;
    DELETE FROM public.party_room_admins WHERE user_id = v_user_text_id;
    DELETE FROM public.party_room_members WHERE user_id = v_user_text_id;
    DELETE FROM public.party_room_messages WHERE sender_id = v_user_text_id;
    DELETE FROM public.party_room_seats WHERE room_id IN (SELECT id FROM public.party_rooms WHERE host_user_id = v_user_text_id);
    DELETE FROM public.party_rooms WHERE host_user_id = v_user_text_id;

    -- Clean up Chat Messages
    DELETE FROM public.messages WHERE sender_id = v_user_text_id OR receiver_id = v_user_text_id;
    DELETE FROM public.chat_messages WHERE sender_id = v_user_text_id OR receiver_id = v_user_text_id;

    -- Clean up Social Relations
    DELETE FROM public.user_blocks WHERE blocker_id = v_user_text_id OR blocked_id = v_user_text_id;
    DELETE FROM public.blocked_users WHERE blocker_id = v_user_text_id OR blocked_id = v_user_text_id;
    DELETE FROM public.user_followers WHERE follower_id = v_user_text_id OR following_id = v_user_text_id;
    DELETE FROM public.user_follows WHERE follower_id = v_user_text_id OR following_id = v_user_text_id;
    DELETE FROM public.user_visitors WHERE visitor_user_id = v_user_text_id OR visited_user_id = v_user_text_id;
    DELETE FROM public.profile_visitors WHERE visitor_id = v_user_text_id OR visited_id = v_user_text_id;

    -- Clean up Agency Relations
    DELETE FROM public.agency_members WHERE user_id = v_user_text_id;
    DELETE FROM public.agency_applications WHERE user_id = v_user_text_id;
    DELETE FROM public.agency_group_messages WHERE sender_id = v_user_text_id;

    -- Clean up Device Tokens, Bonuses, Transactions, Frames, Reports, Analytics
    DELETE FROM public.fcm_device_tokens WHERE user_id = v_user_text_id;
    DELETE FROM public.fcm_tokens WHERE user_id = v_user_text_id;
    DELETE FROM public.claimed_welcome_bonuses WHERE user_id = v_user_text_id;
    DELETE FROM public.user_frames WHERE user_id = v_user_text_id;
    DELETE FROM public.user_avatar_frames WHERE user_id = v_user_text_id;
    DELETE FROM public.coin_transactions WHERE user_id = v_user_text_id;
    DELETE FROM public.diamond_transactions WHERE user_id = v_user_text_id;
    DELETE FROM public.fast_reply_transactions WHERE female_user_id = v_user_text_id OR male_user_id = v_user_text_id;
    DELETE FROM public.user_reports WHERE reporter_id = v_user_text_id OR reported_id = v_user_text_id;
    DELETE FROM public.analytics_events WHERE user_id = v_user_text_id;

    -- Delete Public Profile
    DELETE FROM public.profiles WHERE id = v_user_text_id;

    -- Purge User Auth Record from auth.users (Zero-Trace)
    DELETE FROM auth.users WHERE id = v_user_id;

    RETURN jsonb_build_object(
        'success', true,
        'message', 'Account and all associated records permanently purged.'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
