-- ============================================================================
-- QIVO FCM PUSH NOTIFICATION SETUP SCRIPT
-- Run this in your Supabase Project -> SQL Editor
--
-- Strict Constraints:
-- Push notifications are ONLY used for:
-- 1. New chat messages (CHAT_MESSAGE)
-- 2. Incoming calls (INCOMING_CALL)
-- ============================================================================

-- 1. Ensure fcm_device_tokens table exists with proper indexes and columns
CREATE TABLE IF NOT EXISTS public.fcm_device_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    device_token TEXT NOT NULL UNIQUE,
    platform TEXT DEFAULT 'android',
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Index for fast token lookup by user_id
CREATE INDEX IF NOT EXISTS idx_fcm_device_tokens_user_id ON public.fcm_device_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_fcm_device_tokens_token ON public.fcm_device_tokens (device_token);

-- Backward compatibility table: fcm_tokens
CREATE TABLE IF NOT EXISTS public.fcm_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    token TEXT NOT NULL UNIQUE,
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_fcm_tokens_user_id ON public.fcm_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_fcm_tokens_token ON public.fcm_tokens (token);

-- 2. Enable Row Level Security (RLS)
ALTER TABLE public.fcm_device_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fcm_tokens ENABLE ROW LEVEL SECURITY;

-- 3. RLS Policies: Authenticated users can insert, update, select, and delete their own tokens
DROP POLICY IF EXISTS "Authenticated users manage own device tokens" ON public.fcm_device_tokens;
CREATE POLICY "Authenticated users manage own device tokens" ON public.fcm_device_tokens
    FOR ALL TO authenticated
    USING (auth.uid()::text = user_id::text)
    WITH CHECK (auth.uid()::text = user_id::text);

DROP POLICY IF EXISTS "Service role full access on fcm_device_tokens" ON public.fcm_device_tokens;
CREATE POLICY "Service role full access on fcm_device_tokens" ON public.fcm_device_tokens
    FOR ALL TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "Authenticated users manage own fcm tokens" ON public.fcm_tokens;
CREATE POLICY "Authenticated users manage own fcm tokens" ON public.fcm_tokens
    FOR ALL TO authenticated
    USING (auth.uid()::text = user_id::text)
    WITH CHECK (auth.uid()::text = user_id::text);

DROP POLICY IF EXISTS "Service role full access on fcm_tokens" ON public.fcm_tokens;
CREATE POLICY "Service role full access on fcm_tokens" ON public.fcm_tokens
    FOR ALL TO service_role
    USING (true)
    WITH CHECK (true);

-- 4. Grant privileges
GRANT ALL ON public.fcm_device_tokens TO authenticated, service_role;
GRANT ALL ON public.fcm_tokens TO authenticated, service_role;

-- Verification query
SELECT count(*) AS total_registered_device_tokens FROM public.fcm_device_tokens;
