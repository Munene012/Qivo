-- ============================================================================
-- QIVO DEVICE RESTRICTION & SECURE COIN AWARD ENHANCEMENTS
-- ============================================================================

-- 1. Device Account Registry
-- Stores hashed device IDs to enforce 1-account-per-device policy.
CREATE TABLE IF NOT EXISTS public.signup_device_accounts (
    device_id_hash TEXT PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- RLS: Only service_role can read/write this table for security.
ALTER TABLE public.signup_device_accounts ENABLE ROW LEVEL SECURITY;
GRANT ALL ON public.signup_device_accounts TO service_role;
GRANT SELECT ON public.signup_device_accounts TO authenticated;

-- 2. RPC: check_or_register_signup_device
-- Atomically checks if a device is already registered and registers it if not.
CREATE OR REPLACE FUNCTION public.check_or_register_signup_device(
    p_qivo_device_id TEXT,
    p_user_id UUID
)
RETURNS JSONB AS $$
DECLARE
    v_device_hash TEXT;
    v_existing_user_id UUID;
BEGIN
    IF p_qivo_device_id IS NULL OR p_qivo_device_id = '' THEN
        RETURN jsonb_build_object('success', true, 'message', 'Skipping: No device ID provided');
    END IF;

    -- Hash the device ID for privacy and storage efficiency
    v_device_hash := encode(digest(p_qivo_device_id, 'sha256'), 'hex');

    -- Lock the row if it exists
    SELECT user_id INTO v_existing_user_id
    FROM public.signup_device_accounts
    WHERE device_id_hash = v_device_hash
    FOR UPDATE;

    IF FOUND THEN
        -- If already registered to THIS user, it's fine (idempotent)
        IF v_existing_user_id = p_user_id THEN
            RETURN jsonb_build_object('success', true, 'message', 'Device already registered to this account');
        ELSE
            -- REJECT: Device already has another account
            RETURN jsonb_build_object(
                'success', false, 
                'code', 'DEVICE_ALREADY_REGISTERED',
                'message', 'Only one account per device is allowed. A QIVO account has already been created on this device.'
            );
        END IF;
    END IF;

    -- Register the device
    INSERT INTO public.signup_device_accounts (device_id_hash, user_id)
    VALUES (v_device_hash, p_user_id);

    RETURN jsonb_build_object('success', true, 'message', 'Device registered successfully');
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.check_or_register_signup_device(TEXT, UUID) TO service_role, authenticated;


-- 3. SECURE award_coins RPC (Hardened)
-- Fixed locking order and stricter verification.
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
    v_first_id TEXT;
    v_second_id TEXT;
BEGIN
    p_sender_id := trim(COALESCE(p_sender_id, ''));
    IF p_amount <= 0 THEN
        RETURN jsonb_build_object('success', false, 'error', 'INVALID_AMOUNT', 'message', 'Amount must be greater than 0');
    END IF;
    IF p_amount > 1000000 THEN
        RETURN jsonb_build_object('success', false, 'error', 'INVALID_AMOUNT', 'message', 'Amount exceeds max limit of 1,000,000');
    END IF;

    -- 1. Find target recipient profile (without lock first to get ID)
    SELECT id::text, COALESCE(name, 'User')
    INTO v_target_id, v_target_name
    FROM public.profiles
    WHERE numeric_id = p_target_numeric_id;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'TARGET_NOT_FOUND', 'message', 'Recipient not found');
    END IF;

    -- 2. Verify sender profile and status (without lock first to get ID)
    SELECT id::text, COALESCE(coins, 0), COALESCE(is_admin, false), COALESCE(is_coinseller, false)
    INTO v_sender_actual_id, v_sender_coins, v_sender_db_admin, v_sender_db_seller
    FROM public.profiles
    WHERE (p_sender_id <> '' AND id::text = p_sender_id)
       OR (p_sender_numeric_id > 0 AND numeric_id = p_sender_numeric_id);

    IF v_sender_actual_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'SENDER_NOT_FOUND', 'message', 'Sender profile not found');
    END IF;

    IF v_sender_actual_id = v_target_id THEN
        RETURN jsonb_build_object('success', false, 'error', 'INVALID_TARGET', 'message', 'You cannot award coins to yourself');
    END IF;

    v_effective_admin := COALESCE(v_sender_db_admin, false);
    
    -- If caller claims to be coinseller but DB says no, reject
    IF NOT v_effective_admin AND NOT v_sender_db_seller THEN
        RETURN jsonb_build_object('success', false, 'error', 'UNAUTHORIZED', 'message', 'You do not have permission to award coins');
    END IF;

    -- 3. CONSISTENT LOCKING ORDER to prevent deadlocks
    IF v_sender_actual_id < v_target_id THEN
        v_first_id := v_sender_actual_id;
        v_second_id := v_target_id;
    ELSE
        v_first_id := v_target_id;
        v_second_id := v_sender_actual_id;
    END IF;

    PERFORM 1 FROM public.profiles WHERE id::text = v_first_id FOR UPDATE;
    PERFORM 1 FROM public.profiles WHERE id::text = v_second_id FOR UPDATE;

    -- Re-fetch data after locks
    SELECT coins INTO v_sender_coins FROM public.profiles WHERE id::text = v_sender_actual_id;

    -- 4. If NOT admin -> Sender is Coin Seller: DEDUCT from coin seller's balance
    IF NOT v_effective_admin THEN
        IF v_sender_coins < p_amount THEN
            RETURN jsonb_build_object('success', false, 'error', 'INSUFFICIENT_BALANCE', 'message', 'Insufficient balance!');
        END IF;

        UPDATE public.profiles
        SET coins = coins - p_amount, updated_at = now()
        WHERE id::text = v_sender_actual_id
        RETURNING coins INTO v_seller_new_coins;

        INSERT INTO public.coin_transactions (user_id, amount, type, title, description, created_at)
        VALUES (v_sender_actual_id, -p_amount, 'TRANSFER_OUT', 'Coins Transferred', 'Sent ' || p_amount || ' coins to ' || v_target_name, now());
    ELSE
        v_seller_new_coins := v_sender_coins;
    END IF;

    -- 5. Credit target recipient
    UPDATE public.profiles
    SET coins = COALESCE(coins, 0) + p_amount, updated_at = now()
    WHERE id::text = v_target_id
    RETURNING coins INTO v_target_new_coins;

    INSERT INTO public.coin_transactions (user_id, amount, type, title, description, created_at)
    VALUES (v_target_id, p_amount, 'AWARD', 'Coins Received', COALESCE(NULLIF(trim(p_reason), ''), 'Awarded by Admin/Seller'), now());

    RETURN jsonb_build_object(
        'success', true,
        'message', 'Success!',
        'seller_coins', v_seller_new_coins,
        'target_coins', v_target_new_coins,
        'target_user_id', v_target_id
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.award_coins(TEXT, BIGINT, BOOLEAN, BOOLEAN, BIGINT, BIGINT, TEXT) TO service_role;
