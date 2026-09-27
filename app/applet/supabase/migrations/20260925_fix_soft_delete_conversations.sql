-- Migration: Fix soft deleted chat conversations
-- Description: Creates persistent table public.deleted_conversations to store per-user deletion cutoff timestamps
-- so deleted conversations remain deleted forever, and only new messages sent/received after the deletion cutoff appear.

CREATE TABLE IF NOT EXISTS public.deleted_conversations (
    id BIGSERIAL PRIMARY KEY,
    user_id TEXT NOT NULL,
    partner_id TEXT NOT NULL,
    deleted_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    CONSTRAINT uq_deleted_conversations_user_partner UNIQUE (user_id, partner_id)
);

-- Index for high-performance lookup
CREATE INDEX IF NOT EXISTS idx_deleted_conversations_user_partner 
ON public.deleted_conversations (user_id, partner_id);

-- Enable Row Level Security (RLS)
ALTER TABLE public.deleted_conversations ENABLE ROW LEVEL SECURITY;

-- Drop existing policies if any
DROP POLICY IF EXISTS "Allow select for all on deleted_conversations" ON public.deleted_conversations;
DROP POLICY IF EXISTS "Allow insert for all on deleted_conversations" ON public.deleted_conversations;
DROP POLICY IF EXISTS "Allow update for all on deleted_conversations" ON public.deleted_conversations;
DROP POLICY IF EXISTS "Allow delete for all on deleted_conversations" ON public.deleted_conversations;

-- Permissive policies for anon / authenticated access
CREATE POLICY "Allow select for all on deleted_conversations" 
ON public.deleted_conversations FOR SELECT USING (true);

CREATE POLICY "Allow insert for all on deleted_conversations" 
ON public.deleted_conversations FOR INSERT WITH CHECK (true);

CREATE POLICY "Allow update for all on deleted_conversations" 
ON public.deleted_conversations FOR UPDATE USING (true);

CREATE POLICY "Allow delete for all on deleted_conversations" 
ON public.deleted_conversations FOR DELETE USING (true);

-- RPC: Record or update a soft deletion timestamp
CREATE OR REPLACE FUNCTION public.soft_delete_conversation(
    p_user_id TEXT,
    p_partner_id TEXT
)
RETURNS TIMESTAMPTZ
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_now TIMESTAMPTZ := timezone('utc'::text, now());
BEGIN
    INSERT INTO public.deleted_conversations (user_id, partner_id, deleted_at)
    VALUES (TRIM(p_user_id), TRIM(p_partner_id), v_now)
    ON CONFLICT (user_id, partner_id)
    DO UPDATE SET deleted_at = EXCLUDED.deleted_at;

    RETURN v_now;
END;
$$;

-- Grant permissions to public, authenticated, and service_role
GRANT ALL ON TABLE public.deleted_conversations TO anon, authenticated, service_role;
GRANT USAGE, SELECT ON SEQUENCE public.deleted_conversations_id_seq TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.soft_delete_conversation(TEXT, TEXT) TO anon, authenticated, service_role;
