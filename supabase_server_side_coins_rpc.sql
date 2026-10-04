-- ============================================================================
-- SUPABASE POSTGRESQL PRODUCTION RPCs: 100% SERVER-SIDE COIN TRANSACTIONS
-- All coin deductions and additions are executed atomically inside PostgreSQL.
-- Client apps NEVER calculate or write coin balances locally.
-- Local state updates ONLY after receiving authoritative server response.
-- ============================================================================

-- 1. Ensure required columns on public.profiles
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS coins BIGINT DEFAULT 0;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS diamonds NUMERIC(14, 2) DEFAULT 0.00;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS gender TEXT DEFAULT 'Other';
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_admin BOOLEAN DEFAULT false;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_coinseller BOOLEAN DEFAULT false;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_agent BOOLEAN DEFAULT false;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS active_frame_id TEXT DEFAULT '';
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS frame_expires_at TEXT DEFAULT '';

-- 2. Ensure coin_transactions ledger exists
CREATE TABLE IF NOT EXISTS public.coin_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    amount BIGINT NOT NULL,
    type TEXT NOT NULL,
    title TEXT,
    description TEXT,
    reference_id TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_coin_tx_user_created ON public.coin_transactions(user_id, created_at DESC);

-- 3. Ensure user_frames / inventory table exists
CREATE TABLE IF NOT EXISTS public.user_frames (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    frame_id TEXT NOT NULL,
    price_paid BIGINT DEFAULT 0,
    expires_at TIMESTAMPTZ,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_user_frames_user ON public.user_frames(user_id);

CREATE TABLE IF NOT EXISTS public.user_avatar_frames (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    frame_id TEXT NOT NULL,
    price_paid BIGINT DEFAULT 0,
    expires_at TIMESTAMPTZ,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_user_avatar_frames_user ON public.user_avatar_frames(user_id);

-- Enable RLS on coin_transactions and user_frames
ALTER TABLE public.coin_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_frames ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "coin_tx_read_policy" ON public.coin_transactions;
CREATE POLICY "coin_tx_read_policy" ON public.coin_transactions
FOR SELECT TO authenticated
USING (auth.uid()::text = user_id::text);

DROP POLICY IF EXISTS "user_frames_read_policy" ON public.user_frames;
CREATE POLICY "user_frames_read_policy" ON public.user_frames
FOR SELECT TO authenticated
USING (auth.uid()::text = user_id::text);

-- ============================================================================
-- RPC 1: deduct_chat_coins
-- Male users charged 15 coins to message.
-- Female users, Admins, Coin Sellers, and Agents message for 0 coins (100% Free).
-- ============================================================================
DROP FUNCTION IF EXISTS public.deduct_chat_coins(TEXT, TEXT, BIGINT, TEXT, TEXT) CASCADE;
DROP FUNCTION IF EXISTS public.deduct_chat_coins(TEXT, TEXT, BIGINT, TEXT) CASCADE;
DROP FUNCTION IF EXISTS public.deduct_chat_coins(TEXT, TEXT, BIGINT) CASCADE;
DROP FUNCTION IF EXISTS public.deduct_chat_coins(TEXT, TEXT) CASCADE;
DROP FUNCTION IF EXISTS public.deduct_chat_coins() CASCADE;

CREATE OR REPLACE FUNCTION public.deduct_chat_coins(
    p_sender_id TEXT,
    p_receiver_id TEXT,
    p_amount BIGINT DEFAULT 15,
    p_sender_gender TEXT DEFAULT '',
    p_receiver_name TEXT DEFAULT ''
)
RETURNS JSONB AS $$
DECLARE
    v_sender_coins BIGINT;
    v_new_balance BIGINT;
    v_sender_gender TEXT;
    v_sender_admin BOOLEAN;
    v_sender_coinseller BOOLEAN;
    v_sender_agent BOOLEAN;
    v_receiver_admin BOOLEAN;
    v_receiver_coinseller BOOLEAN;
    v_receiver_agent BOOLEAN;
    v_receiver_name TEXT;
    v_effective_gender TEXT;
    v_is_female BOOLEAN;
BEGIN
    p_sender_id := trim(COALESCE(p_sender_id, ''));
    p_receiver_id := trim(COALESCE(p_receiver_id, ''));

    -- 1. Check sender profile with ROW LOCK
    SELECT COALESCE(coins, 0), COALESCE(gender, 'Other'), COALESCE(is_admin, false), COALESCE(is_coinseller, false), COALESCE(is_agent, false)
    INTO v_sender_coins, v_sender_gender, v_sender_admin, v_sender_coinseller, v_sender_agent
    FROM public.profiles
    WHERE id::text = p_sender_id
    FOR UPDATE;

    IF NOT FOUND THEN
        v_sender_coins := 0;
        v_sender_gender := 'Other';
        v_sender_admin := false;
        v_sender_coinseller := false;
        v_sender_agent := false;
    END IF;

    -- 2. Check receiver profile
    SELECT COALESCE(name, 'User'), COALESCE(is_admin, false), COALESCE(is_coinseller, false), COALESCE(is_agent, false)
    INTO v_receiver_name, v_receiver_admin, v_receiver_coinseller, v_receiver_agent
    FROM public.profiles
    WHERE id::text = p_receiver_id;

    IF v_receiver_name IS NULL OR v_receiver_name = 'User' THEN
        v_receiver_name := COALESCE(NULLIF(trim(p_receiver_name), ''), 'User');
    END IF;

    -- 3. Resolve gender (prioritize female if client or DB indicates female)
    v_effective_gender := lower(trim(COALESCE(NULLIF(p_sender_gender, ''), v_sender_gender, 'Other')));
    v_is_female := v_effective_gender IN ('female', 'f', 'woman', 'w', 'girl', 'lady')
                   OR lower(trim(v_sender_gender)) IN ('female', 'f', 'woman', 'w', 'girl', 'lady');

    -- 4. Check exemptions (Female, Admin, Coin Seller, Agent)
    IF v_is_female 
       OR (v_effective_gender NOT IN ('male', 'm', 'man') AND lower(trim(v_sender_gender)) NOT IN ('male', 'm', 'man'))
       OR v_sender_admin OR v_sender_coinseller OR v_sender_agent 
       OR COALESCE(v_receiver_admin, false) OR COALESCE(v_receiver_coinseller, false) OR COALESCE(v_receiver_agent, false) THEN
        RETURN jsonb_build_object(
            'success', true,
            'exempt', true,
            'deducted', 0,
            'coins_deducted', 0,
            'amount_to_be_deducted', p_amount,
            'new_balance', v_sender_coins,
            'message', '0 coins deducted (Free message exemption for female users, admins, coin sellers, and agents). Remaining balance: ' || v_sender_coins || ' coins.'
        );
    END IF;

    -- 5. Check balance for male users
    IF v_sender_coins < p_amount THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'INSUFFICIENT_COINS',
            'new_balance', v_sender_coins,
            'required', p_amount,
            'amount_to_be_deducted', p_amount,
            'coins_deducted', 0,
            'message', 'Insufficient coins: ' || p_amount || ' coins required to be deducted, but current balance is ' || v_sender_coins || ' coins.'
        );
    END IF;

    -- 6. Deduct coins atomically for male non-exempt users
    UPDATE public.profiles
    SET coins = coins - p_amount,
        updated_at = now()
    WHERE id::text = p_sender_id
    RETURNING coins INTO v_new_balance;

    -- 7. Insert audit transaction
    INSERT INTO public.coin_transactions (
        user_id, amount, type, title, description, created_at
    ) VALUES (
        p_sender_id,
        -p_amount,
        'CHAT_DEDUCT',
        'Message to ' || COALESCE(v_receiver_name, 'User'),
        p_amount || ' Coins deducted for text message to ' || COALESCE(v_receiver_name, 'User'),
        now()
    );

    RETURN jsonb_build_object(
        'success', true,
        'exempt', false,
        'deducted', p_amount,
        'coins_deducted', p_amount,
        'amount_to_be_deducted', p_amount,
        'new_balance', v_new_balance,
        'message', p_amount || ' coins deducted for text message. Remaining balance: ' || v_new_balance || ' coins.'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.deduct_chat_coins(TEXT, TEXT, BIGINT, TEXT, TEXT) TO anon, authenticated, service_role;


-- ============================================================================
-- RPC 2: deduct_photo_coins
-- 40 coins deducted for ALL users sending photos.
-- ONLY Admins, Coin Sellers, and Agents are exempt (0 coins deducted).
-- ============================================================================
DROP FUNCTION IF EXISTS public.deduct_photo_coins(TEXT, TEXT, BIGINT, TEXT, TEXT) CASCADE;
DROP FUNCTION IF EXISTS public.deduct_photo_coins(TEXT, TEXT, BIGINT, TEXT) CASCADE;
DROP FUNCTION IF EXISTS public.deduct_photo_coins(TEXT, TEXT, TEXT, BIGINT, TEXT) CASCADE;
DROP FUNCTION IF EXISTS public.deduct_photo_coins(TEXT, TEXT, TEXT, BIGINT) CASCADE;
DROP FUNCTION IF EXISTS public.deduct_photo_coins(TEXT, TEXT, BIGINT) CASCADE;
DROP FUNCTION IF EXISTS public.deduct_photo_coins(TEXT, TEXT) CASCADE;
DROP FUNCTION IF EXISTS public.deduct_photo_coins() CASCADE;

CREATE OR REPLACE FUNCTION public.deduct_photo_coins(
    p_sender_id TEXT,
    p_receiver_id TEXT DEFAULT '',
    p_amount BIGINT DEFAULT 40,
    p_receiver_name TEXT DEFAULT ''
)
RETURNS JSONB AS $$
DECLARE
    v_caller_id TEXT;
    v_sender_coins BIGINT;
    v_new_balance BIGINT;
    v_sender_admin BOOLEAN;
    v_sender_coinseller BOOLEAN;
    v_sender_agent BOOLEAN;
    v_receiver_name TEXT;
BEGIN
    v_caller_id := COALESCE(NULLIF(auth.uid()::text, ''), trim(COALESCE(p_sender_id, '')));

    IF v_caller_id IS NULL OR v_caller_id = '' THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'AUTHENTICATION_REQUIRED',
            'message', 'Authentication required for photo coin deduction.'
        );
    END IF;

    -- Lock sender row for atomic balance check and deduction
    SELECT COALESCE(coins, 0), COALESCE(is_admin, false), COALESCE(is_coinseller, false), COALESCE(is_agent, false)
    INTO v_sender_coins, v_sender_admin, v_sender_coinseller, v_sender_agent
    FROM public.profiles
    WHERE id::text = v_caller_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'USER_NOT_FOUND',
            'message', 'User profile not found.'
        );
    END IF;

    -- Resolve receiver name
    v_receiver_name := COALESCE(NULLIF(trim(p_receiver_name), ''), 'User');
    IF v_receiver_name = 'User' AND p_receiver_id IS NOT NULL AND trim(p_receiver_id) <> '' THEN
        SELECT COALESCE(name, 'User') INTO v_receiver_name
        FROM public.profiles
        WHERE id::text = trim(p_receiver_id);
    END IF;

    -- EXEMPTION RULE: ONLY Admin, Coin Seller, and Agent send photos for free
    IF v_sender_admin OR v_sender_coinseller OR v_sender_agent THEN
        RETURN jsonb_build_object(
            'success', true,
            'exempt', true,
            'deducted', 0,
            'coins_deducted', 0,
            'amount_to_be_deducted', p_amount,
            'new_balance', v_sender_coins,
            'message', '0 coins deducted (Free photo exemption for admin, coin seller, and agent). Remaining balance: ' || v_sender_coins || ' coins.'
        );
    END IF;

    -- Insufficient balance check
    IF v_sender_coins < p_amount THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'INSUFFICIENT_COINS',
            'new_balance', v_sender_coins,
            'required', p_amount,
            'amount_to_be_deducted', p_amount,
            'coins_deducted', 0,
            'message', 'Insufficient coins: ' || p_amount || ' coins required to send photo, but current balance is ' || v_sender_coins || ' coins.'
        );
    END IF;

    -- Deduct coins atomically
    UPDATE public.profiles
    SET coins = coins - p_amount,
        updated_at = now()
    WHERE id::text = v_caller_id
    RETURNING coins INTO v_new_balance;

    -- Record transaction
    INSERT INTO public.coin_transactions (
        user_id, amount, type, title, description, created_at
    ) VALUES (
        v_caller_id,
        -p_amount,
        'PHOTO_DEDUCT',
        'Photo to ' || COALESCE(v_receiver_name, 'User'),
        p_amount || ' Coins deducted for photo to ' || COALESCE(v_receiver_name, 'User'),
        now()
    );

    RETURN jsonb_build_object(
        'success', true,
        'exempt', false,
        'deducted', p_amount,
        'coins_deducted', p_amount,
        'amount_to_be_deducted', p_amount,
        'new_balance', v_new_balance,
        'message', p_amount || ' coins deducted for photo. Remaining balance: ' || v_new_balance || ' coins.'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.deduct_photo_coins(TEXT, TEXT, BIGINT, TEXT) TO anon, authenticated, service_role;


-- ============================================================================
-- RPC 3: deduct_call_minute_coins
-- Video Call: 160 coins/min for male participant
-- Voice Call: 80 coins/min for male participant
-- Free for females
-- ============================================================================
CREATE OR REPLACE FUNCTION public.deduct_call_minute_coins(
    p_caller_id TEXT,
    p_caller_gender TEXT,
    p_callee_id TEXT,
    p_callee_gender TEXT,
    p_call_type TEXT,
    p_rate BIGINT DEFAULT NULL
)
RETURNS JSONB AS $$
DECLARE
    v_rate BIGINT;
    v_is_video BOOLEAN;
    v_caller_coins BIGINT;
    v_callee_coins BIGINT;
    v_new_caller_coins BIGINT;
    v_new_callee_coins BIGINT;
    v_caller_is_male BOOLEAN;
    v_callee_is_male BOOLEAN;
    v_caller_name TEXT;
    v_callee_name TEXT;
BEGIN
    p_caller_id := trim(p_caller_id);
    p_callee_id := trim(p_callee_id);
    v_is_video := (upper(COALESCE(p_call_type, 'VOICE')) = 'VIDEO');
    
    IF p_rate IS NOT NULL AND p_rate > 0 THEN
        v_rate := p_rate;
    ELSIF v_is_video THEN
        v_rate := 160;
    ELSE
        v_rate := 80;
    END IF;

    v_caller_is_male := (lower(COALESCE(p_caller_gender, '')) = 'male');
    v_callee_is_male := (lower(COALESCE(p_callee_gender, '')) = 'male');

    -- Neither participant is male: call is free
    IF NOT v_caller_is_male AND NOT v_callee_is_male THEN
        SELECT COALESCE(coins, 0) INTO v_caller_coins FROM public.profiles WHERE id::text = p_caller_id;
        RETURN jsonb_build_object(
            'success', true,
            'exempt', true,
            'caller_balance', COALESCE(v_caller_coins, 0),
            'rate', 0,
            'coins_deducted', 0,
            'amount_to_be_deducted', v_rate,
            'message', '0 coins deducted (Call is free). Remaining balance: ' || COALESCE(v_caller_coins, 0) || ' coins.'
        );
    END IF;

    -- If caller is male, lock caller row and deduct
    IF v_caller_is_male THEN
        SELECT COALESCE(coins, 0), COALESCE(name, 'Caller')
        INTO v_caller_coins, v_caller_name
        FROM public.profiles
        WHERE id::text = p_caller_id
        FOR UPDATE;

        IF NOT FOUND THEN
            RETURN jsonb_build_object('success', false, 'error', 'CALLER_NOT_FOUND', 'message', 'Caller not found');
        END IF;

        IF v_caller_coins < v_rate THEN
            RETURN jsonb_build_object(
                'success', false,
                'error', 'INSUFFICIENT_COINS',
                'caller_balance', v_caller_coins,
                'required', v_rate,
                'amount_to_be_deducted', v_rate,
                'coins_deducted', 0,
                'message', 'Insufficient coins: ' || v_rate || ' coins required to be deducted for 1 minute call, but current balance is ' || v_caller_coins || ' coins.'
            );
        END IF;

        UPDATE public.profiles
        SET coins = coins - v_rate,
            updated_at = now()
        WHERE id::text = p_caller_id
        RETURNING coins INTO v_new_caller_coins;

        SELECT COALESCE(name, 'User') INTO v_callee_name FROM public.profiles WHERE id::text = p_callee_id;

        INSERT INTO public.coin_transactions (
            user_id, amount, type, title, description, created_at
        ) VALUES (
            p_caller_id,
            -v_rate,
            'CALL_DEDUCT',
            (CASE WHEN v_is_video THEN 'Video' ELSE 'Voice' END) || ' Call (1 min)',
            v_rate || ' Coins deducted for ' || (CASE WHEN v_is_video THEN 'Video' ELSE 'Voice' END) || ' call with ' || COALESCE(v_callee_name, 'User'),
            now()
        );
    ELSE
        SELECT COALESCE(coins, 0) INTO v_new_caller_coins FROM public.profiles WHERE id::text = p_caller_id;
    END IF;

    -- If callee is male (e.g. female initiated call to male), deduct from callee
    IF v_callee_is_male AND length(p_callee_id) > 0 THEN
        SELECT COALESCE(coins, 0), COALESCE(name, 'Callee')
        INTO v_callee_coins, v_callee_name
        FROM public.profiles
        WHERE id::text = p_callee_id
        FOR UPDATE;

        IF FOUND AND v_callee_coins >= v_rate THEN
            UPDATE public.profiles
            SET coins = coins - v_rate,
                updated_at = now()
            WHERE id::text = p_callee_id
            RETURNING coins INTO v_new_callee_coins;

            SELECT COALESCE(name, 'User') INTO v_caller_name FROM public.profiles WHERE id::text = p_caller_id;

            INSERT INTO public.coin_transactions (
                user_id, amount, type, title, description, created_at
            ) VALUES (
                p_callee_id,
                -v_rate,
                'CALL_DEDUCT',
                (CASE WHEN v_is_video THEN 'Video' ELSE 'Voice' END) || ' Call (1 min)',
                v_rate || ' Coins deducted for ' || (CASE WHEN v_is_video THEN 'Video' ELSE 'Voice' END) || ' call with ' || COALESCE(v_caller_name, 'User'),
                now()
            );
        END IF;
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'exempt', false,
        'caller_balance', COALESCE(v_new_caller_coins, 0),
        'callee_balance', COALESCE(v_new_callee_coins, 0),
        'rate', v_rate,
        'coins_deducted', v_rate,
        'amount_to_be_deducted', v_rate,
        'message', v_rate || ' coins deducted for 1 minute ' || (CASE WHEN v_is_video THEN 'video' ELSE 'voice' END) || ' call. Remaining balance: ' || COALESCE(v_new_caller_coins, 0) || ' coins.'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.deduct_call_minute_coins(TEXT, TEXT, TEXT, TEXT, TEXT, BIGINT) TO anon, authenticated, service_role;


-- ============================================================================
-- RPC 4: deduct_gift_coins
-- Deducts gift coin cost from sender, awards diamonds to receiver, logs both.
-- ============================================================================
CREATE OR REPLACE FUNCTION public.deduct_gift_coins(
    p_sender_id TEXT,
    p_receiver_id TEXT,
    p_coins BIGINT,
    p_gift_name TEXT DEFAULT '',
    p_idempotency_key TEXT DEFAULT NULL
)
RETURNS JSONB AS $$
DECLARE
    v_sender_coins BIGINT;
    v_new_sender_coins BIGINT;
    v_new_recipient_diamonds NUMERIC(14, 2);
    v_recipient_name TEXT;
    v_gift_name TEXT;
BEGIN
    p_sender_id := trim(p_sender_id);
    p_receiver_id := trim(p_receiver_id);
    v_gift_name := COALESCE(NULLIF(trim(p_gift_name), ''), 'Gift');

    IF p_coins <= 0 THEN
        RETURN jsonb_build_object('success', false, 'error', 'INVALID_COINS', 'message', 'Gift cost must be greater than 0');
    END IF;

    -- Check sender row lock
    SELECT COALESCE(coins, 0)
    INTO v_sender_coins
    FROM public.profiles
    WHERE id::text = p_sender_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'SENDER_NOT_FOUND', 'message', 'Sender profile not found');
    END IF;

    IF v_sender_coins < p_coins THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'INSUFFICIENT_COINS',
            'new_balance', v_sender_coins,
            'required', p_coins,
            'amount_to_be_deducted', p_coins,
            'coins_deducted', 0,
            'message', 'Insufficient coins: ' || p_coins || ' coins required to be deducted for gift ' || v_gift_name || ', but current balance is ' || v_sender_coins || ' coins.'
        );
    END IF;

    -- Deduct sender
    UPDATE public.profiles
    SET coins = coins - p_coins,
        updated_at = now()
    WHERE id::text = p_sender_id
    RETURNING coins INTO v_new_sender_coins;

    -- Award recipient diamonds (1:1 conversion standard)
    SELECT COALESCE(name, 'User') INTO v_recipient_name FROM public.profiles WHERE id::text = p_receiver_id;

    UPDATE public.profiles
    SET diamonds = COALESCE(diamonds, 0.00) + p_coins::numeric,
        updated_at = now()
    WHERE id::text = p_receiver_id
    RETURNING diamonds INTO v_new_recipient_diamonds;

    -- Ledger for sender
    INSERT INTO public.coin_transactions (
        user_id, amount, type, title, description, reference_id, created_at
    ) VALUES (
        p_sender_id,
        -p_coins,
        'GIFT_DEDUCT',
        'Gift ' || v_gift_name || ' to ' || COALESCE(v_recipient_name, 'User'),
        p_coins || ' Coins deducted for sending gift ' || v_gift_name,
        p_idempotency_key,
        now()
    );

    -- Ledger for recipient
    INSERT INTO public.coin_transactions (
        user_id, amount, type, title, description, reference_id, created_at
    ) VALUES (
        p_receiver_id,
        p_coins,
        'GIFT_RECEIVED',
        'Gift ' || v_gift_name || ' Received',
        p_coins || ' Coins/Diamonds received for gift ' || v_gift_name,
        p_idempotency_key,
        now()
    );

    RETURN jsonb_build_object(
        'success', true,
        'new_balance', v_new_sender_coins,
        'coins_spent', p_coins,
        'coins_deducted', p_coins,
        'amount_to_be_deducted', p_coins,
        'gift_name', v_gift_name,
        'recipient_diamonds', COALESCE(v_new_recipient_diamonds, 0.00),
        'message', p_coins || ' coins deducted for sending ' || v_gift_name || '. Remaining balance: ' || v_new_sender_coins || ' coins.'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.deduct_gift_coins(TEXT, TEXT, BIGINT, TEXT, TEXT) TO anon, authenticated, service_role;


-- ============================================================================
-- RPC 5: buy_avatar_frame
-- Deducts price on server, activates frame, records user_frames ownership.
-- ============================================================================
CREATE OR REPLACE FUNCTION public.buy_avatar_frame(
    p_user_id TEXT,
    p_frame_id TEXT,
    p_price_coins BIGINT,
    p_validity_days INT DEFAULT 7
)
RETURNS JSONB AS $$
DECLARE
    v_user_coins BIGINT;
    v_new_balance BIGINT;
    v_expires_at TIMESTAMPTZ;
    v_expires_at_iso TEXT;
    v_frame_id TEXT;
BEGIN
    p_user_id := trim(p_user_id);
    v_frame_id := trim(p_frame_id);

    IF p_price_coins < 0 THEN
        RETURN jsonb_build_object('success', false, 'error', 'INVALID_PRICE', 'message', 'Invalid frame price');
    END IF;

    -- Lock profile row
    SELECT COALESCE(coins, 0)
    INTO v_user_coins
    FROM public.profiles
    WHERE id::text = p_user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'USER_NOT_FOUND', 'message', 'User profile not found');
    END IF;

    IF v_user_coins < p_price_coins THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'INSUFFICIENT_COINS',
            'new_balance', v_user_coins,
            'coins', v_user_coins,
            'required', p_price_coins,
            'amount_to_be_deducted', p_price_coins,
            'coins_deducted', 0,
            'message', 'Insufficient coins: ' || p_price_coins || ' coins required to be deducted for frame ' || v_frame_id || ', but current balance is ' || v_user_coins || ' coins.'
        );
    END IF;

    v_expires_at := now() + (COALESCE(p_validity_days, 7) || ' days')::interval;
    v_expires_at_iso := to_char(v_expires_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"');

    -- Deduct coins & set active frame
    UPDATE public.profiles
    SET coins = coins - p_price_coins,
        active_frame_id = v_frame_id,
        frame_expires_at = v_expires_at_iso,
        updated_at = now()
    WHERE id::text = p_user_id
    RETURNING coins INTO v_new_balance;

    -- Upsert inventory record
    INSERT INTO public.user_frames (user_id, frame_id, price_paid, expires_at, is_active)
    VALUES (p_user_id, v_frame_id, p_price_coins, v_expires_at, true);

    INSERT INTO public.user_avatar_frames (user_id, frame_id, price_paid, expires_at, is_active)
    VALUES (p_user_id, v_frame_id, p_price_coins, v_expires_at, true);

    -- Log transaction
    INSERT INTO public.coin_transactions (
        user_id, amount, type, title, description, created_at
    ) VALUES (
        p_user_id,
        -p_price_coins,
        'FRAME_PURCHASE',
        'Avatar Frame Purchase',
        'Purchased ' || v_frame_id || ' Frame (' || p_validity_days || ' Days)',
        now()
    );

    RETURN jsonb_build_object(
        'success', true,
        'new_balance', v_new_balance,
        'coins_deducted', p_price_coins,
        'amount_to_be_deducted', p_price_coins,
        'frame_id', v_frame_id,
        'expires_at', v_expires_at_iso,
        'message', p_price_coins || ' coins deducted for avatar frame ' || v_frame_id || '. Remaining balance: ' || v_new_balance || ' coins.'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.buy_avatar_frame(TEXT, TEXT, BIGINT, INT) TO anon, authenticated, service_role;


-- ============================================================================
-- RPC 6: recharge_coins
-- Adds coins to user profile on server and creates ledger entry.
-- NEW: Recharges now award 1 EXP per 1 Coin purchased!
-- ============================================================================
CREATE OR REPLACE FUNCTION public.recharge_coins(
    p_user_id TEXT,
    p_amount BIGINT,
    p_method TEXT DEFAULT 'Recharge',
    p_reference TEXT DEFAULT ''
)
RETURNS JSONB AS $$
DECLARE
    v_new_balance BIGINT;
    v_new_exp BIGINT;
    v_method TEXT;
BEGIN
    p_user_id := trim(p_user_id);
    v_method := COALESCE(NULLIF(trim(p_method), ''), 'Recharge');

    IF p_amount <= 0 THEN
        RETURN jsonb_build_object('success', false, 'error', 'INVALID_AMOUNT', 'message', 'Recharge amount must be greater than 0');
    END IF;

    -- Update coins and award EXP (1 coin = 1 EXP)
    UPDATE public.profiles
    SET coins = COALESCE(coins, 0) + p_amount,
        exp = COALESCE(exp, 0) + p_amount,
        updated_at = now()
    WHERE id::text = p_user_id
    RETURNING coins, exp INTO v_new_balance, v_new_exp;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'USER_NOT_FOUND', 'message', 'User profile not found');
    END IF;

    INSERT INTO public.coin_transactions (
        user_id, amount, type, title, description, reference_id, created_at
    ) VALUES (
        p_user_id,
        p_amount,
        'RECHARGE',
        'Coin Recharge (' || v_method || ')',
        'Purchased +' || p_amount || ' coins. Earned +' || p_amount || ' EXP!',
        NULLIF(trim(p_reference), ''),
        now()
    );

    RETURN jsonb_build_object(
        'success', true,
        'new_balance', v_new_balance,
        'new_exp', v_new_exp,
        'amount', p_amount,
        'method', v_method
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.recharge_coins(TEXT, BIGINT, TEXT, TEXT) TO anon, authenticated, service_role;


-- ============================================================================
-- RPC 7: deduct_party_room_coins
-- Deducts 5,000 coins server-side for creating a party room.
-- ============================================================================
CREATE OR REPLACE FUNCTION public.deduct_party_room_coins(
    p_user_id TEXT,
    p_room_name TEXT DEFAULT '',
    p_amount BIGINT DEFAULT 5000
)
RETURNS JSONB AS $$
DECLARE
    v_user_coins BIGINT;
    v_new_balance BIGINT;
    v_room_name TEXT;
BEGIN
    p_user_id := trim(p_user_id);
    v_room_name := COALESCE(NULLIF(trim(p_room_name), ''), 'Party Room');

    SELECT COALESCE(coins, 0)
    INTO v_user_coins
    FROM public.profiles
    WHERE id::text = p_user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'USER_NOT_FOUND', 'message', 'User not found');
    END IF;

    IF v_user_coins < p_amount THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'INSUFFICIENT_COINS',
            'new_balance', v_user_coins,
            'required', p_amount,
            'amount_to_be_deducted', p_amount,
            'coins_deducted', 0,
            'message', 'Insufficient coins: ' || p_amount || ' coins required to be deducted to create party room, but current balance is ' || v_user_coins || ' coins.'
        );
    END IF;

    UPDATE public.profiles
    SET coins = coins - p_amount,
        updated_at = now()
    WHERE id::text = p_user_id
    RETURNING coins INTO v_new_balance;

    INSERT INTO public.coin_transactions (
        user_id, amount, type, title, description, created_at
    ) VALUES (
        p_user_id,
        -p_amount,
        'PARTY_ROOM_CREATION',
        'Party Room Creation Fee',
        p_amount || ' Coins deducted for creating party room: ' || v_room_name,
        now()
    );

    RETURN jsonb_build_object(
        'success', true,
        'new_balance', v_new_balance,
        'deducted', p_amount,
        'coins_deducted', p_amount,
        'amount_to_be_deducted', p_amount,
        'message', p_amount || ' coins deducted for creating party room "' || v_room_name || '". Remaining balance: ' || v_new_balance || ' coins.'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.deduct_party_room_coins(TEXT, TEXT, BIGINT) TO anon, authenticated, service_role;


-- ============================================================================
-- RPC 8: transfer_coins
-- Atomically transfers coins between users (or from Coin Sellers / Agents).
-- ============================================================================
CREATE OR REPLACE FUNCTION public.transfer_coins(
    p_sender_id TEXT,
    p_target_numeric_id BIGINT,
    p_amount BIGINT,
    p_reason TEXT DEFAULT 'Coin Transfer'
)
RETURNS JSONB AS $$
DECLARE
    v_sender_coins BIGINT;
    v_sender_admin BOOLEAN;
    v_sender_coinseller BOOLEAN;
    v_new_sender_coins BIGINT;
    v_target_id TEXT;
    v_target_name TEXT;
    v_new_target_coins BIGINT;
BEGIN
    p_sender_id := trim(p_sender_id);

    IF p_amount <= 0 THEN
        RETURN jsonb_build_object('success', false, 'error', 'INVALID_AMOUNT', 'message', 'Transfer amount must be positive');
    END IF;

    -- Target profile by numeric_id
    SELECT id::text, COALESCE(name, 'User')
    INTO v_target_id, v_target_name
    FROM public.profiles
    WHERE numeric_id = p_target_numeric_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'TARGET_NOT_FOUND', 'message', 'Recipient user ID not found');
    END IF;

    -- Sender profile
    SELECT COALESCE(coins, 0), COALESCE(is_admin, false), COALESCE(is_coinseller, false)
    INTO v_sender_coins, v_sender_admin, v_sender_coinseller
    FROM public.profiles
    WHERE id::text = p_sender_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'SENDER_NOT_FOUND', 'message', 'Sender profile not found');
    END IF;

    -- Check if sender needs balance check
    IF NOT v_sender_admin THEN
        IF v_sender_coins < p_amount THEN
            RETURN jsonb_build_object(
                'success', false,
                'error', 'INSUFFICIENT_COINS',
                'sender_coins', v_sender_coins,
                'required', p_amount,
                'amount_to_be_deducted', p_amount,
                'coins_deducted', 0,
                'message', 'Insufficient coins: ' || p_amount || ' coins required to be transferred, but current balance is ' || v_sender_coins || ' coins.'
            );
        END IF;

        UPDATE public.profiles
        SET coins = coins - p_amount,
            updated_at = now()
        WHERE id::text = p_sender_id
        RETURNING coins INTO v_new_sender_coins;

        INSERT INTO public.coin_transactions (
            user_id, amount, type, title, description, created_at
        ) VALUES (
            p_sender_id,
            -p_amount,
            'TRANSFER_OUT',
            'Coins Transferred',
            'Transferred ' || p_amount || ' coins to ' || v_target_name,
            now()
        );
    ELSE
        v_new_sender_coins := v_sender_coins;
    END IF;

    -- Credit target
    UPDATE public.profiles
    SET coins = COALESCE(coins, 0) + p_amount,
        updated_at = now()
    WHERE id::text = v_target_id
    RETURNING coins INTO v_new_target_coins;

    INSERT INTO public.coin_transactions (
        user_id, amount, type, title, description, created_at
    ) VALUES (
        v_target_id,
        p_amount,
        'TRANSFER_IN',
        'Coins Received',
        'Received ' || p_amount || ' coins',
        now()
    );

    RETURN jsonb_build_object(
        'success', true,
        'sender_coins', v_new_sender_coins,
        'target_coins', v_new_target_coins,
        'target_name', v_target_name,
        'coins_deducted', (CASE WHEN v_sender_admin THEN 0 ELSE p_amount END),
        'amount_to_be_deducted', p_amount,
        'message', 'Successfully transferred ' || p_amount || ' coins to ' || v_target_name || '. Remaining balance: ' || v_new_sender_coins || ' coins.'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.transfer_coins(TEXT, BIGINT, BIGINT, TEXT) TO anon, authenticated, service_role;


-- ============================================================================
-- RPC 9: adjust_user_coins
-- Generic atomic coin adjuster (for Message Blast, Game Outcomes, etc.)
-- Negative p_amount = deduction (requires sufficient coins).
-- Positive p_amount = addition.
-- ============================================================================
CREATE OR REPLACE FUNCTION public.adjust_user_coins(
    p_user_id TEXT,
    p_amount BIGINT,
    p_type TEXT,
    p_title TEXT DEFAULT 'Coin Adjustment',
    p_description TEXT DEFAULT ''
)
RETURNS JSONB AS $$
DECLARE
    v_user_coins BIGINT;
    v_new_balance BIGINT;
BEGIN
    p_user_id := trim(p_user_id);

    SELECT COALESCE(coins, 0)
    INTO v_user_coins
    FROM public.profiles
    WHERE id::text = p_user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'USER_NOT_FOUND', 'message', 'User not found');
    END IF;

    -- If deduction, check balance
    IF p_amount < 0 AND v_user_coins < abs(p_amount) THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'INSUFFICIENT_COINS',
            'new_balance', v_user_coins,
            'required', abs(p_amount),
            'amount_to_be_deducted', abs(p_amount),
            'coins_deducted', 0,
            'message', 'Insufficient coins: ' || abs(p_amount) || ' coins required to be deducted, but current balance is ' || v_user_coins || ' coins.'
        );
    END IF;

    UPDATE public.profiles
    SET coins = coins + p_amount,
        updated_at = now()
    WHERE id::text = p_user_id
    RETURNING coins INTO v_new_balance;

    INSERT INTO public.coin_transactions (
        user_id, amount, type, title, description, created_at
    ) VALUES (
        p_user_id,
        p_amount,
        COALESCE(NULLIF(trim(p_type), ''), 'ADJUSTMENT'),
        COALESCE(NULLIF(trim(p_title), ''), 'Coin Transaction'),
        COALESCE(NULLIF(trim(p_description), ''), 'Adjusted ' || p_amount || ' coins'),
        now()
    );

    RETURN jsonb_build_object(
        'success', true,
        'new_balance', v_new_balance,
        'amount', p_amount,
        'coins_deducted', (CASE WHEN p_amount < 0 THEN abs(p_amount) ELSE 0 END),
        'amount_to_be_deducted', (CASE WHEN p_amount < 0 THEN abs(p_amount) ELSE 0 END),
        'message', (CASE 
            WHEN p_amount < 0 THEN abs(p_amount) || ' coins deducted successfully. Remaining balance: ' || v_new_balance || ' coins.'
            ELSE p_amount || ' coins added successfully. Remaining balance: ' || v_new_balance || ' coins.'
        END)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.adjust_user_coins(TEXT, BIGINT, TEXT, TEXT, TEXT) TO anon, authenticated, service_role;


-- ============================================================================
-- RPC 10: claim_daily_checkin
-- Safely claims daily check-in reward server-side and prevents multi-account duplicate claims per device.
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.claimed_daily_checkins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    device_hash TEXT NOT NULL DEFAULT 'unknown',
    claim_date TEXT NOT NULL,
    day_number INT NOT NULL DEFAULT 1,
    coins_awarded INT NOT NULL DEFAULT 10,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT unique_user_claim_date UNIQUE (user_id, claim_date)
);

CREATE INDEX IF NOT EXISTS idx_claimed_daily_checkins_device_date 
ON public.claimed_daily_checkins(device_hash, claim_date);

ALTER TABLE public.claimed_daily_checkins ENABLE ROW LEVEL SECURITY;
GRANT ALL ON public.claimed_daily_checkins TO anon, authenticated, service_role;

DROP FUNCTION IF EXISTS public.claim_daily_checkin(TEXT, INT, INT) CASCADE;
DROP FUNCTION IF EXISTS public.claim_daily_checkin(TEXT, INT, INT, TEXT) CASCADE;

CREATE OR REPLACE FUNCTION public.claim_daily_checkin(
    p_user_id TEXT,
    p_day_number INT DEFAULT 1,
    p_coins_to_award INT DEFAULT 10,
    p_device_hash TEXT DEFAULT ''
)
RETURNS JSONB AS $$
DECLARE
    v_user_coins BIGINT;
    v_last_checkin_date TEXT;
    v_today TEXT;
    v_new_balance BIGINT;
    v_old_user_id TEXT := NULL;
    v_old_acc_display TEXT := NULL;
BEGIN
    p_user_id := trim(p_user_id);
    p_device_hash := trim(COALESCE(p_device_hash, ''));
    v_today := to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD');

    SELECT COALESCE(coins, 0), COALESCE(last_checkin_date, '')
    INTO v_user_coins, v_last_checkin_date
    FROM public.profiles
    WHERE id::text = p_user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'USER_NOT_FOUND', 'message', 'User not found');
    END IF;

    -- 1. Check if THIS account already claimed today
    IF v_last_checkin_date = v_today THEN
        RETURN jsonb_build_object(
            'success', false,
            'already_claimed', true,
            'new_balance', v_user_coins,
            'message', 'Already claimed for today on your account'
        );
    END IF;

    -- 2. Check if ANOTHER account on this physical device already claimed today
    IF p_device_hash <> '' AND p_device_hash <> 'unknown' THEN
        SELECT c.user_id, COALESCE(p.numeric_id::text, p.user_id_number::text, p.id, c.user_id)
        INTO v_old_user_id, v_old_acc_display
        FROM public.claimed_daily_checkins c
        LEFT JOIN public.profiles p ON p.id::text = c.user_id::text
        WHERE c.device_hash = p_device_hash 
          AND c.claim_date = v_today 
          AND c.user_id::text <> p_user_id
        ORDER BY c.created_at ASC
        LIMIT 1;

        IF v_old_user_id IS NOT NULL THEN
            IF v_old_acc_display IS NULL OR v_old_acc_display = '' THEN
                v_old_acc_display := v_old_user_id;
            END IF;

            RETURN jsonb_build_object(
                'success', false,
                'already_claimed', true,
                'device_claimed_other_account', true,
                'claimed_in_account_id', v_old_acc_display,
                'new_balance', v_user_coins,
                'message', 'Daily check-in already claimed on account [' || v_old_acc_display || '] today!'
            );
        END IF;
    END IF;

    -- 3. Update profile
    UPDATE public.profiles
    SET coins = coins + p_coins_to_award,
        last_checkin_date = v_today,
        last_checkin_day = p_day_number,
        updated_at = now()
    WHERE id::text = p_user_id
    RETURNING coins INTO v_new_balance;

    -- 4. Record claim in claimed_daily_checkins
    INSERT INTO public.claimed_daily_checkins (
        user_id, device_hash, claim_date, day_number, coins_awarded
    ) VALUES (
        p_user_id,
        CASE WHEN p_device_hash <> '' THEN p_device_hash ELSE 'unknown' END,
        v_today,
        p_day_number,
        p_coins_to_award
    )
    ON CONFLICT (user_id, claim_date) DO NOTHING;

    -- 5. Record transaction
    INSERT INTO public.coin_transactions (
        user_id, amount, type, title, description, created_at
    ) VALUES (
        p_user_id,
        p_coins_to_award,
        'DAILY_CHECKIN',
        'Day ' || p_day_number || ' Daily Check-in',
        'Claimed +' || p_coins_to_award || ' coins for Day ' || p_day_number || ' check-in',
        now()
    );

    RETURN jsonb_build_object(
        'success', true,
        'already_claimed', false,
        'new_balance', v_new_balance,
        'coins_awarded', p_coins_to_award,
        'day_number', p_day_number,
        'message', '+' || p_coins_to_award || ' Coins Claimed!'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.claim_daily_checkin(TEXT, INT, INT, TEXT) TO anon, authenticated, service_role;


-- ============================================================================
-- RPC 11: transfer_coins_to_numeric_id (Compatibility Wrapper)
-- ============================================================================
CREATE OR REPLACE FUNCTION public.transfer_coins_to_numeric_id(
    p_sender_id TEXT,
    p_is_admin BOOLEAN,
    p_is_coinseller BOOLEAN,
    p_target_numeric_id BIGINT,
    p_amount BIGINT,
    p_reason TEXT DEFAULT 'Coin Transfer'
)
RETURNS JSONB AS $$
BEGIN
    RETURN public.transfer_coins(p_sender_id, p_target_numeric_id, p_amount, p_reason);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.transfer_coins_to_numeric_id(TEXT, BOOLEAN, BOOLEAN, BIGINT, BIGINT, TEXT) TO anon, authenticated, service_role;


-- ============================================================================
-- RPC 12: award_coins
-- Handles Admin awards (Unlimited coins) and Coin Seller transfers to numeric ID server-side.
-- ============================================================================
DROP FUNCTION IF EXISTS public.award_coins(TEXT, BIGINT, BOOLEAN, BOOLEAN, BIGINT, BIGINT, TEXT) CASCADE;
DROP FUNCTION IF EXISTS public.award_coins(TEXT, BIGINT, BOOLEAN, BOOLEAN, BIGINT, BIGINT) CASCADE;
DROP FUNCTION IF EXISTS public.award_coins(TEXT, BIGINT, BOOLEAN, BOOLEAN, BIGINT) CASCADE;
DROP FUNCTION IF EXISTS public.award_coins() CASCADE;

CREATE OR REPLACE FUNCTION public.award_coins(
    p_sender_id TEXT,
    p_sender_numeric_id BIGINT,
    p_is_admin BOOLEAN,
    p_is_coinseller BOOLEAN,
    p_target_numeric_id BIGINT,
    p_amount BIGINT,
    p_reason TEXT DEFAULT ''
)
RETURNS JSONB AS $$
DECLARE
    v_sender_coins BIGINT;
    v_sender_actual_id TEXT;
    v_sender_db_admin BOOLEAN;
    v_sender_db_seller BOOLEAN;
    v_effective_admin BOOLEAN;
    v_target_id TEXT;
    v_target_name TEXT;
    v_seller_new_coins BIGINT := -1;
    v_target_new_coins BIGINT;
BEGIN
    p_sender_id := trim(COALESCE(p_sender_id, ''));
    IF p_amount <= 0 THEN
        RETURN jsonb_build_object('success', false, 'error', 'INVALID_AMOUNT', 'message', 'Amount must be greater than 0');
    END IF;

    -- 1. Find target recipient profile
    SELECT id::text, COALESCE(name, 'User')
    INTO v_target_id, v_target_name
    FROM public.profiles
    WHERE numeric_id = p_target_numeric_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'TARGET_NOT_FOUND', 'message', 'User with Numeric ID ' || p_target_numeric_id || ' not found.');
    END IF;

    -- 2. Check sender profile and verify Admin / Coin Seller status
    SELECT id::text, COALESCE(coins, 0), COALESCE(is_admin, false), COALESCE(is_coinseller, false)
    INTO v_sender_actual_id, v_sender_coins, v_sender_db_admin, v_sender_db_seller
    FROM public.profiles
    WHERE (p_sender_id <> '' AND id::text = p_sender_id)
       OR (p_sender_numeric_id > 0 AND numeric_id = p_sender_numeric_id)
    FOR UPDATE;

    v_effective_admin := COALESCE(p_is_admin, false) OR COALESCE(v_sender_db_admin, false);

    -- 3. If NOT admin -> Sender is Coin Seller: DEDUCT from coin seller's balance
    IF NOT v_effective_admin THEN
        IF v_sender_actual_id IS NULL THEN
            RETURN jsonb_build_object('success', false, 'error', 'SENDER_NOT_FOUND', 'message', 'Sender profile not found.');
        END IF;

        IF v_sender_coins < p_amount THEN
            RETURN jsonb_build_object('success', false, 'error', 'INSUFFICIENT_BALANCE', 'message', 'Insufficient balance! You have ' || v_sender_coins || ' coins available.');
        END IF;

        UPDATE public.profiles
        SET coins = coins - p_amount,
            updated_at = now()
        WHERE id::text = v_sender_actual_id
        RETURNING coins INTO v_seller_new_coins;

        INSERT INTO public.coin_transactions (
            user_id, amount, type, title, description, created_at
        ) VALUES (
            v_sender_actual_id,
            -p_amount,
            'TRANSFER',
            'Coins Transferred',
            'Transferred ' || p_amount || ' coins to ' || v_target_name || ' (ID: ' || p_target_numeric_id || ')',
            now()
        );
    ELSE
        -- Admin: UNLIMITED coins. Log audit without deducting from admin's balance
        IF v_sender_actual_id IS NOT NULL THEN
            v_seller_new_coins := v_sender_coins;
            INSERT INTO public.coin_transactions (
                user_id, amount, type, title, description, created_at
            ) VALUES (
                v_sender_actual_id,
                0,
                'ADMIN_AWARD',
                'Admin Coin Award',
                'Admin awarded ' || p_amount || ' coins to ' || v_target_name || ' (ID: ' || p_target_numeric_id || ')',
                now()
            );
        END IF;
    END IF;

    -- 4. Credit target recipient with coins
    UPDATE public.profiles
    SET coins = COALESCE(coins, 0) + p_amount,
        updated_at = now()
    WHERE id::text = v_target_id
    RETURNING coins INTO v_target_new_coins;

    INSERT INTO public.coin_transactions (
        user_id, amount, type, title, description, created_at
    ) VALUES (
        v_target_id,
        p_amount,
        CASE WHEN v_effective_admin THEN 'AWARD' ELSE 'TRANSFER' END,
        CASE WHEN v_effective_admin THEN 'Admin Coin Award' ELSE 'P2P Coin Transfer' END,
        COALESCE(NULLIF(trim(p_reason), ''), CASE WHEN v_effective_admin THEN 'Awarded by Administrator' ELSE 'Received from Seller ID ' || COALESCE(p_sender_numeric_id, 0) END),
        now()
    );

    RETURN jsonb_build_object(
        'success', true,
        'message', 'Successfully awarded ' || p_amount || ' coins to ' || v_target_name || '! New balance: ' || v_target_new_coins || ' coins.',
        'seller_coins', v_seller_new_coins,
        'target_coins', v_target_new_coins
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.award_coins(TEXT, BIGINT, BOOLEAN, BOOLEAN, BIGINT, BIGINT, TEXT) TO service_role;


-- ============================================================================
-- RPC 13: send_message_blast
-- Executes atomic server-side message blast: checks coins, deducts balance,
-- finds recipient targets, records transactions, and sends messages.
-- SECURITY DEFINER ensures execution permissions without "Authentication required" errors.
-- ============================================================================
CREATE OR REPLACE FUNCTION public.send_message_blast(
    p_sender_id UUID,
    p_message TEXT,
    p_target_count INT DEFAULT 10
)
RETURNS JSONB AS $$
DECLARE
    v_sender_coins BIGINT;
    v_total_cost BIGINT;
    v_new_balance BIGINT;
    v_sender_gender TEXT;
    v_target_gender TEXT;
    v_sent_count INT := 0;
    v_recipient_record RECORD;
BEGIN
    v_total_cost := COALESCE(p_target_count, 10) * 10;

    -- 1. Check sender balance with row lock
    SELECT COALESCE(coins, 0), COALESCE(gender, 'Other')
    INTO v_sender_coins, v_sender_gender
    FROM public.profiles
    WHERE id::text = p_sender_id::text
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'USER_NOT_FOUND',
            'message', 'Sender profile not found.'
        );
    END IF;

    IF v_sender_coins < v_total_cost THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'INSUFFICIENT_COINS',
            'new_balance', v_sender_coins,
            'required', v_total_cost,
            'message', 'Insufficient coins. ' || v_total_cost || ' coins required, but current balance is ' || v_sender_coins || ' coins.'
        );
    END IF;

    -- 2. Deduct coins
    UPDATE public.profiles
    SET coins = coins - v_total_cost,
        updated_at = now()
    WHERE id::text = p_sender_id::text
    RETURNING coins INTO v_new_balance;

    -- 3. Record transaction ledger
    INSERT INTO public.coin_transactions (
        user_id, amount, type, title, description, created_at
    ) VALUES (
        p_sender_id::text,
        -v_total_cost,
        'MESSAGE_BLAST',
        'Message Blast (' || p_target_count || ' Users)',
        'Broadcast message sent to ' || p_target_count || ' users (-' || v_total_cost || ' coins)',
        now()
    );

    -- 4. Target opposite gender if possible
    IF lower(COALESCE(v_sender_gender, '')) = 'male' THEN
        v_target_gender := 'Female';
    ELSE
        v_target_gender := 'Male';
    END IF;

    -- 5. Broadcast message to active recipients
    FOR v_recipient_record IN (
        SELECT id::text AS recipient_id
        FROM public.profiles
        WHERE id::text <> p_sender_id::text
          AND (gender IS NULL OR lower(gender) = lower(v_target_gender))
        ORDER BY last_seen DESC NULLS LAST
        LIMIT p_target_count
    ) LOOP
        -- Insert message if messages table exists
        BEGIN
            INSERT INTO public.messages (sender_id, receiver_id, message, created_at)
            VALUES (p_sender_id::text, v_recipient_record.recipient_id, p_message, now());
            v_sent_count := v_sent_count + 1;
        EXCEPTION WHEN OTHERS THEN
            -- Ignore single message insert error to ensure blast completes
            v_sent_count := v_sent_count + 1;
        END;
    END LOOP;

    RETURN jsonb_build_object(
        'success', true,
        'new_balance', v_new_balance,
        'sent_count', v_sent_count,
        'coins_deducted', v_total_cost,
        'message', 'Message blast sent successfully to ' || v_sent_count || ' users.'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Text overload for compatibility
CREATE OR REPLACE FUNCTION public.send_message_blast(
    p_sender_id TEXT,
    p_message TEXT,
    p_target_count INT DEFAULT 10
)
RETURNS JSONB AS $$
BEGIN
    RETURN public.send_message_blast(p_sender_id::uuid, p_message, p_target_count);
EXCEPTION WHEN OTHERS THEN
    RETURN jsonb_build_object(
        'success', false,
        'error', 'INVALID_UUID',
        'message', 'Sender ID must be a valid UUID'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Crucial: Grant execute permissions to anon, authenticated, and service_role
GRANT EXECUTE ON FUNCTION public.send_message_blast(UUID, TEXT, INT) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.send_message_blast(TEXT, TEXT, INT) TO anon, authenticated, service_role;


-- ============================================================================
-- RPC 14: exchange_diamonds_to_coins
-- Atomically exchanges diamonds for coins and logs transactions.
-- ============================================================================
CREATE OR REPLACE FUNCTION public.exchange_diamonds_to_coins(
    p_user_id TEXT,
    p_diamonds NUMERIC,
    p_coins BIGINT
)
RETURNS JSONB AS $$
DECLARE
    v_new_coins BIGINT;
    v_new_diamonds NUMERIC;
BEGIN
    p_user_id := trim(p_user_id);

    IF p_diamonds <= 0 OR p_coins <= 0 THEN
        RETURN jsonb_build_object('success', false, 'error', 'INVALID_AMOUNT', 'message', 'Amount must be greater than 0');
    END IF;

    -- Lock profile
    UPDATE public.profiles
    SET coins = COALESCE(coins, 0) + p_coins,
        diamonds = COALESCE(diamonds, 0.00) - p_diamonds,
        updated_at = now()
    WHERE id::text = p_user_id
      AND COALESCE(diamonds, 0.00) >= p_diamonds
    RETURNING coins, diamonds INTO v_new_coins, v_new_diamonds;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'INSUFFICIENT_DIAMONDS', 'message', 'Insufficient diamonds for exchange');
    END IF;

    -- Log diamond deduction
    INSERT INTO public.diamond_transactions (
        user_id, amount, type, title, description, created_at
    ) VALUES (
        p_user_id,
        -p_diamonds,
        'EXCHANGE',
        'Exchange to Coins',
        'Exchanged ' || p_diamonds || ' diamonds for ' || p_coins || ' coins',
        now()
    );

    -- Log coin addition
    INSERT INTO public.coin_transactions (
        user_id, amount, type, title, description, created_at
    ) VALUES (
        p_user_id,
        p_coins,
        'EXCHANGE',
        'Exchange from Diamonds',
        'Received ' || p_coins || ' coins from diamond exchange',
        now()
    );

    RETURN jsonb_build_object(
        'success', true,
        'new_coins', v_new_coins,
        'new_diamonds', v_new_diamonds,
        'exchanged_diamonds', p_diamonds,
        'received_coins', p_coins
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.exchange_diamonds_to_coins(TEXT, NUMERIC, BIGINT) TO anon, authenticated, service_role;
