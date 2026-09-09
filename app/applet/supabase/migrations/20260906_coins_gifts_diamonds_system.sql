-- ============================================================================
-- SECURE COINS, GIFTS, AND DIAMONDS PRODUCTION-GRADE SYSTEM (V2 - TEXT & UUID SAFE)
-- Fixes: operator does not exist: uuid = text (explicit type casting auth.uid()::text)
-- Features: Consecutive Gifting + Time Percentage Conversion (with exact decimals e.g. 86.55)
-- ============================================================================

-- 1. Ensure columns on profiles table
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS coins BIGINT DEFAULT 0;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS diamonds NUMERIC(14, 2) DEFAULT 0.00;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS gender TEXT DEFAULT 'Other';

-- Upgrade diamonds to NUMERIC(14, 2) to preserve exact decimal values without truncation
ALTER TABLE public.profiles ALTER COLUMN diamonds TYPE NUMERIC(14, 2) USING diamonds::numeric;
ALTER TABLE public.profiles ALTER COLUMN diamonds SET DEFAULT 0.00;
ALTER TABLE public.profiles ALTER COLUMN coins SET DEFAULT 0;

-- Ensure primary key exists on profiles.id
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conrelid = 'public.profiles'::regclass 
          AND contype IN ('p', 'u')
    ) THEN
        ALTER TABLE public.profiles ADD PRIMARY KEY (id);
    END IF;
END $$;

-- 2. Official Gifts Catalog (Authoritative server values)
CREATE TABLE IF NOT EXISTS public.gifts (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    emoji TEXT NOT NULL,
    coin_cost BIGINT NOT NULL CHECK (coin_cost > 0),
    diamond_value NUMERIC(14, 2) NOT NULL CHECK (diamond_value >= 0),
    category TEXT DEFAULT 'Popular',
    badge TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Populate authoritative gifts (1:1 base conversion rate)
INSERT INTO public.gifts (id, name, emoji, coin_cost, diamond_value, category, badge, is_active) VALUES
('gift_rose', 'Rose', '🌹', 10, 10.00, 'Popular', '10 c', true),
('gift_heart', 'Love Heart', '💖', 20, 20.00, 'Popular', 'HOT', true),
('gift_choco', 'Chocolates', '🍫', 30, 30.00, 'Popular', NULL, true),
('gift_icecream', 'Ice Cream', '🍦', 50, 50.00, 'Popular', NULL, true),
('gift_crown', 'Royal Crown', '👑', 100, 100.00, 'Popular', 'POPULAR', true),
('gift_letter', 'Love Letter', '💌', 200, 200.00, 'Luxury', NULL, true),
('gift_ring', 'Diamond Ring', '💍', 300, 300.00, 'Luxury', 'SHINE', true),
('gift_cake', 'Party Cake', '🎂', 50, 50.00, 'Luxury', NULL, true),
('gift_magic', 'Magic Wand', '✨', 500, 500.00, 'Luxury', NULL, true),
('gift_fireworks', 'Fireworks', '🎆', 1000, 1000.00, 'Luxury', '1,000 c', true),
('gift_car', 'Sports Car', '🏎️', 2500, 2500.00, 'VIP & Mythic', '2.5K c', true),
('gift_yacht', 'Golden Yacht', '🛥️', 5000, 5000.00, 'VIP & Mythic', '5K c', true),
('gift_jet', 'Private Jet', '✈️', 10000, 10000.00, 'VIP & Mythic', '10K c', true),
('gift_castle', 'Royal Castle', '🏰', 25000, 25000.00, 'VIP & Mythic', '25K VIP', true),
('gift_galaxy', 'Universe Galaxy', '🌌', 50000, 50000.00, 'VIP & Mythic', '50K MYTHIC', true)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    emoji = EXCLUDED.emoji,
    coin_cost = EXCLUDED.coin_cost,
    diamond_value = EXCLUDED.diamond_value,
    is_active = EXCLUDED.is_active;

-- 3. Coin Transactions Ledger (user_id as TEXT for universal compatibility)
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

-- 4. Diamond Transactions Ledger (user_id as TEXT, amount as NUMERIC(14, 2))
CREATE TABLE IF NOT EXISTS public.diamond_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    type TEXT,
    transaction_type TEXT NOT NULL,
    title TEXT,
    description TEXT,
    reference_id TEXT,
    status TEXT DEFAULT 'Completed',
    created_at TIMESTAMPTZ DEFAULT now()
);
ALTER TABLE public.diamond_transactions ALTER COLUMN amount TYPE NUMERIC(14, 2) USING amount::numeric;
CREATE INDEX IF NOT EXISTS idx_diamond_tx_user_created ON public.diamond_transactions(user_id, created_at DESC);

-- 5. Gift Transactions Ledger (sender_id and recipient_id as TEXT)
CREATE TABLE IF NOT EXISTS public.gift_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key TEXT NOT NULL,
    sender_id TEXT NOT NULL,
    recipient_id TEXT NOT NULL,
    gift_id TEXT NOT NULL REFERENCES public.gifts(id),
    coins_spent BIGINT NOT NULL CHECK (coins_spent > 0),
    diamonds_awarded NUMERIC(14, 2) NOT NULL CHECK (diamonds_awarded >= 0),
    time_percentage NUMERIC(6, 4) DEFAULT 1.0000,
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT gift_transactions_idempotency_key_key UNIQUE (idempotency_key)
);
ALTER TABLE public.gift_transactions ADD COLUMN IF NOT EXISTS time_percentage NUMERIC(6, 4) DEFAULT 1.0000;
CREATE INDEX IF NOT EXISTS idx_gift_transactions_sender ON public.gift_transactions(sender_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_gift_transactions_recipient ON public.gift_transactions(recipient_id, created_at DESC);

-- 6. Fast Reply Transactions Ledger
CREATE TABLE IF NOT EXISTS public.fast_reply_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key TEXT NOT NULL,
    female_user_id TEXT NOT NULL,
    male_user_id TEXT NOT NULL,
    original_message_id BIGINT NOT NULL,
    reply_message_id BIGINT NOT NULL,
    response_seconds NUMERIC(10, 2) NOT NULL CHECK (response_seconds >= 0 AND response_seconds <= 180),
    diamonds_awarded NUMERIC(10, 2) NOT NULL CHECK (diamonds_awarded >= 0 AND diamonds_awarded <= 20),
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT fast_reply_transactions_idempotency_key_key UNIQUE (idempotency_key),
    CONSTRAINT fast_reply_transactions_reply_msg_key UNIQUE (reply_message_id)
);
CREATE INDEX IF NOT EXISTS idx_fast_reply_female ON public.fast_reply_transactions(female_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_fast_reply_male ON public.fast_reply_transactions(male_user_id, created_at DESC);

-- ============================================================================
-- 7. BALANCE PROTECTION TRIGGER (Prevent direct client balance modifications)
-- ============================================================================
CREATE OR REPLACE FUNCTION public.protect_wallet_balances()
RETURNS TRIGGER AS $$
DECLARE
    v_role TEXT;
BEGIN
    BEGIN
        v_role := current_setting('request.jwt.claim.role', true);
    EXCEPTION WHEN OTHERS THEN
        v_role := NULL;
    END;

    IF v_role IN ('authenticated', 'anon') THEN
        IF (OLD.coins IS DISTINCT FROM NEW.coins) OR (OLD.diamonds IS DISTINCT FROM NEW.diamonds) THEN
            RAISE EXCEPTION 'Direct client modification of coins or diamonds is forbidden. Changes must be executed via official secure server RPCs.';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_protect_wallet_balances ON public.profiles;
CREATE TRIGGER trg_protect_wallet_balances
BEFORE UPDATE OF coins, diamonds ON public.profiles
FOR EACH ROW
EXECUTE FUNCTION public.protect_wallet_balances();

-- ============================================================================
-- 8. ROW LEVEL SECURITY (RLS) POLICIES - FIXED TYPE CASTING auth.uid()::text
-- ============================================================================
ALTER TABLE public.gifts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.gift_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fast_reply_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.coin_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.diamond_transactions ENABLE ROW LEVEL SECURITY;

-- Gifts read
DROP POLICY IF EXISTS "gifts_read_policy" ON public.gifts;
CREATE POLICY "gifts_read_policy" ON public.gifts
FOR SELECT USING (is_active = true);

-- Coin Transactions: Explicit cast auth.uid()::text to avoid operator does not exist: uuid = text
DROP POLICY IF EXISTS "coin_transactions_user_read" ON public.coin_transactions;
CREATE POLICY "coin_transactions_user_read" ON public.coin_transactions
FOR SELECT TO authenticated
USING (auth.uid()::text = user_id::text);

-- Diamond Transactions: Explicit cast auth.uid()::text
DROP POLICY IF EXISTS "diamond_transactions_user_read" ON public.diamond_transactions;
CREATE POLICY "diamond_transactions_user_read" ON public.diamond_transactions
FOR SELECT TO authenticated
USING (auth.uid()::text = user_id::text);

-- Gift Transactions: Explicit cast auth.uid()::text
DROP POLICY IF EXISTS "gift_transactions_user_read" ON public.gift_transactions;
CREATE POLICY "gift_transactions_user_read" ON public.gift_transactions
FOR SELECT TO authenticated
USING (auth.uid()::text = sender_id::text OR auth.uid()::text = recipient_id::text);

-- Fast Reply Transactions: Explicit cast auth.uid()::text
DROP POLICY IF EXISTS "fast_reply_transactions_user_read" ON public.fast_reply_transactions;
CREATE POLICY "fast_reply_transactions_user_read" ON public.fast_reply_transactions
FOR SELECT TO authenticated
USING (auth.uid()::text = female_user_id::text OR auth.uid()::text = male_user_id::text);

-- ============================================================================
-- 9. ATOMIC RPC: process_gift(...) WITH CONSECUTIVE GIFTING & TIME PERCENTAGE
-- ============================================================================
CREATE OR REPLACE FUNCTION public.process_gift(
    p_recipient_id TEXT,
    p_gift_id TEXT,
    p_idempotency_key TEXT,
    p_sender_id TEXT DEFAULT NULL,
    p_original_message_id BIGINT DEFAULT NULL
)
RETURNS JSONB AS $$
DECLARE
    v_sender_id TEXT;
    v_recipient_id TEXT;
    v_gift_cost BIGINT;
    v_base_diamonds NUMERIC(14, 2);
    v_diamonds_to_award NUMERIC(14, 2);
    v_gift_name TEXT;
    v_sender_coins BIGINT;
    v_new_sender_coins BIGINT;
    v_new_recipient_diamonds NUMERIC(14, 2);
    v_existing_spent BIGINT;
    v_existing_diamonds NUMERIC(14, 2);
    v_recipient_name TEXT;
    v_orig_created TIMESTAMPTZ;
    v_elapsed_seconds NUMERIC(10, 4);
    v_time_percentage NUMERIC(6, 4) := 1.0000;
BEGIN
    -- 1. Identify & authenticate sender
    v_sender_id := trim(COALESCE(p_sender_id, auth.uid()::text));
    v_recipient_id := trim(p_recipient_id);

    IF v_sender_id IS NULL OR length(v_sender_id) = 0 THEN
        RAISE EXCEPTION 'Unauthorized: Sender identity could not be verified.';
    END IF;

    -- Security: Prevent sender spoofing if called directly from client
    IF auth.uid() IS NOT NULL AND auth.uid()::text <> v_sender_id THEN
        RAISE EXCEPTION 'Forbidden: You cannot send gifts on behalf of another user.';
    END IF;

    -- 2. Validate parameters
    IF v_recipient_id IS NULL OR length(v_recipient_id) = 0 THEN
        RAISE EXCEPTION 'Invalid recipient ID.';
    END IF;

    IF p_idempotency_key IS NULL OR length(trim(p_idempotency_key)) = 0 THEN
        RAISE EXCEPTION 'Idempotency key is required.';
    END IF;

    -- 3. Prevent self-gifting
    IF v_sender_id = v_recipient_id THEN
        RAISE EXCEPTION 'Self-gifting is strictly prohibited.';
    END IF;

    -- 4. Check idempotency: If the exact same transaction key was already processed, return previous result
    SELECT coins_spent, diamonds_awarded INTO v_existing_spent, v_existing_diamonds
    FROM public.gift_transactions
    WHERE idempotency_key = p_idempotency_key;

    IF FOUND THEN
        SELECT coins INTO v_new_sender_coins FROM public.profiles WHERE id::text = v_sender_id;
        SELECT diamonds INTO v_new_recipient_diamonds FROM public.profiles WHERE id::text = v_recipient_id;
        RETURN jsonb_build_object(
            'success', true,
            'duplicate', true,
            'message', 'Transaction previously processed.',
            'idempotency_key', p_idempotency_key,
            'sender_id', v_sender_id,
            'recipient_id', v_recipient_id,
            'gift_id', p_gift_id,
            'coins_spent', v_existing_spent,
            'diamonds_awarded', v_existing_diamonds,
            'new_sender_coins', v_new_sender_coins,
            'new_recipient_diamonds', v_new_recipient_diamonds
        );
    END IF;

    -- 5. Read authoritative gift values from database catalog
    SELECT name, coin_cost, diamond_value INTO v_gift_name, v_gift_cost, v_base_diamonds
    FROM public.gifts
    WHERE id = p_gift_id AND is_active = true;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Gift with id "%" does not exist or is inactive.', p_gift_id;
    END IF;

    -- 6. Check for time percentage conversion if original_message_id is provided
    -- Converts according to remaining percentage of 180-second countdown window
    IF p_original_message_id IS NOT NULL AND p_original_message_id > 0 THEN
        SELECT created_at INTO v_orig_created
        FROM public.messages
        WHERE id = p_original_message_id;

        IF FOUND AND v_orig_created IS NOT NULL THEN
            v_elapsed_seconds := EXTRACT(EPOCH FROM (now() - v_orig_created));
            IF v_elapsed_seconds >= 0 AND v_elapsed_seconds < 180.0 THEN
                -- Calculate exact percentage: e.g. 0.8655 for 86.55% time remaining
                v_time_percentage := ROUND(((180.0 - v_elapsed_seconds) / 180.0)::numeric, 4);
                IF v_time_percentage < 0.05 THEN
                    v_time_percentage := 0.05; -- Minimum 5% floor during window
                END IF;
            ELSE
                v_time_percentage := 1.0000;
            END IF;
        END IF;
    END IF;

    -- Compute diamonds to award with exact 2-decimal precision (e.g. 86.55, 17.86, etc.)
    v_diamonds_to_award := ROUND((v_base_diamonds * v_time_percentage)::numeric, 2);
    IF v_diamonds_to_award <= 0.00 THEN
        v_diamonds_to_award := v_base_diamonds; -- Fallback to standard value
        v_time_percentage := 1.0000;
    END IF;

    -- 7. Verify recipient exists
    SELECT name INTO v_recipient_name FROM public.profiles WHERE id::text = v_recipient_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Recipient profile does not exist.';
    END IF;

    -- 8. Acquire ROW LOCK on sender profile to prevent race conditions and negative balances
    SELECT coins INTO v_sender_coins
    FROM public.profiles
    WHERE id::text = v_sender_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Sender profile does not exist.';
    END IF;

    -- 9. Check coin balance
    IF v_sender_coins < v_gift_cost THEN
        RAISE EXCEPTION 'Insufficient coins. Required: %, Available: %', v_gift_cost, v_sender_coins;
    END IF;

    -- 10. Lock recipient profile row
    PERFORM 1 FROM public.profiles WHERE id::text = v_recipient_id FOR UPDATE;

    -- 11. Deduct coins from sender
    UPDATE public.profiles
    SET coins = coins - v_gift_cost,
        updated_at = now()
    WHERE id::text = v_sender_id
    RETURNING coins INTO v_new_sender_coins;

    -- 12. Add decimal diamonds to recipient profile
    UPDATE public.profiles
    SET diamonds = COALESCE(diamonds, 0.00) + v_diamonds_to_award,
        updated_at = now()
    WHERE id::text = v_recipient_id
    RETURNING diamonds INTO v_new_recipient_diamonds;

    -- 13. Create audit ledgers
    -- A. Gift transaction (stores exact time_percentage & decimal diamonds)
    INSERT INTO public.gift_transactions (
        idempotency_key,
        sender_id,
        recipient_id,
        gift_id,
        coins_spent,
        diamonds_awarded,
        time_percentage,
        created_at
    ) VALUES (
        p_idempotency_key,
        v_sender_id,
        v_recipient_id,
        p_gift_id,
        v_gift_cost,
        v_diamonds_to_award,
        v_time_percentage,
        now()
    );

    -- B. Coin deduction transaction (Sender)
    INSERT INTO public.coin_transactions (
        user_id,
        amount,
        type,
        title,
        description,
        reference_id,
        created_at
    ) VALUES (
        v_sender_id,
        -v_gift_cost,
        'GIFT_SENT',
        'Sent ' || v_gift_name,
        v_gift_cost || ' Coins spent sending ' || v_gift_name || ' to ' || COALESCE(v_recipient_name, 'user'),
        p_idempotency_key,
        now()
    );

    -- C. Diamond award transaction (Recipient with exact decimals like 86.55)
    INSERT INTO public.diamond_transactions (
        user_id,
        amount,
        type,
        transaction_type,
        title,
        description,
        reference_id,
        status,
        created_at
    ) VALUES (
        v_recipient_id,
        v_diamonds_to_award,
        'GIFT_RECEIVED',
        'GIFT_RECEIVED',
        'Received ' || v_gift_name,
        v_diamonds_to_award || ' Diamonds received for gift ' || v_gift_name || 
        (CASE WHEN v_time_percentage < 1.0000 THEN ' (' || ROUND((v_time_percentage * 100)::numeric, 1) || '% time conversion)' ELSE '' END),
        p_idempotency_key,
        'Completed',
        now()
    );

    -- 14. Return atomic result
    RETURN jsonb_build_object(
        'success', true,
        'duplicate', false,
        'idempotency_key', p_idempotency_key,
        'sender_id', v_sender_id,
        'recipient_id', v_recipient_id,
        'gift_id', p_gift_id,
        'gift_name', v_gift_name,
        'coins_spent', v_gift_cost,
        'diamonds_awarded', v_diamonds_to_award,
        'time_percentage', v_time_percentage,
        'new_sender_coins', v_new_sender_coins,
        'new_recipient_diamonds', v_new_recipient_diamonds
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION public.process_gift(TEXT, TEXT, TEXT, TEXT, BIGINT) TO authenticated, service_role;

-- ============================================================================
-- 10. ATOMIC RPC: process_fast_reply_reward(...) (TEXT & UUID SAFE)
-- ============================================================================
CREATE OR REPLACE FUNCTION public.process_fast_reply_reward(
    p_original_message_id BIGINT,
    p_reply_message_id BIGINT,
    p_idempotency_key TEXT,
    p_responder_id TEXT DEFAULT NULL
)
RETURNS JSONB AS $$
DECLARE
    v_responder_id TEXT;
    v_responder_gender TEXT;
    v_orig_sender_id TEXT;
    v_orig_receiver_id TEXT;
    v_orig_created TIMESTAMPTZ;
    v_orig_sender_gender TEXT;
    v_reply_sender_id TEXT;
    v_reply_receiver_id TEXT;
    v_reply_created TIMESTAMPTZ;
    v_response_seconds NUMERIC(10, 4);
    v_reward NUMERIC(10, 2);
    v_new_diamonds NUMERIC(14, 2);
    v_existing_reward NUMERIC(10, 2);
    v_existing_sec NUMERIC(10, 2);
BEGIN
    -- 1. Identify & authenticate responder
    v_responder_id := trim(COALESCE(p_responder_id, auth.uid()::text));
    IF v_responder_id IS NULL OR length(v_responder_id) = 0 THEN
        RAISE EXCEPTION 'Unauthorized: Responder identity could not be verified.';
    END IF;

    IF auth.uid() IS NOT NULL AND auth.uid()::text <> v_responder_id THEN
        RAISE EXCEPTION 'Forbidden: You cannot claim rewards for another user.';
    END IF;

    -- 2. Validate input parameters
    IF p_original_message_id IS NULL OR p_reply_message_id IS NULL THEN
        RAISE EXCEPTION 'Both original_message_id and reply_message_id are required.';
    END IF;

    IF p_original_message_id = p_reply_message_id THEN
        RAISE EXCEPTION 'Original message and reply message cannot be the same.';
    END IF;

    IF p_idempotency_key IS NULL OR length(trim(p_idempotency_key)) = 0 THEN
        RAISE EXCEPTION 'Idempotency key is required.';
    END IF;

    -- 3. Enforce idempotency: If already rewarded, return duplicate response
    SELECT diamonds_awarded, response_seconds INTO v_existing_reward, v_existing_sec
    FROM public.fast_reply_transactions
    WHERE idempotency_key = p_idempotency_key OR reply_message_id = p_reply_message_id;

    IF FOUND THEN
        SELECT diamonds INTO v_new_diamonds FROM public.profiles WHERE id::text = v_responder_id;
        RETURN jsonb_build_object(
            'success', true,
            'duplicate', true,
            'message', 'Fast reply reward previously processed.',
            'idempotency_key', p_idempotency_key,
            'female_user_id', v_responder_id,
            'response_seconds', v_existing_sec,
            'diamonds_awarded', v_existing_reward,
            'new_diamonds', v_new_diamonds
        );
    END IF;

    -- 4. Check responder gender: ONLY female users can earn Fast Reply diamonds
    SELECT gender INTO v_responder_gender FROM public.profiles WHERE id::text = v_responder_id;
    IF LOWER(COALESCE(v_responder_gender, '')) NOT IN ('female', 'woman', 'f') THEN
        RETURN jsonb_build_object(
            'success', false,
            'eligible', false,
            'error', 'Fast Reply diamonds are only eligible for female users.',
            'diamonds_awarded', 0.00
        );
    END IF;

    -- 5. Fetch messages from database (cast sender_id and receiver_id as text)
    SELECT sender_id::text, receiver_id::text, created_at
    INTO v_orig_sender_id, v_orig_receiver_id, v_orig_created
    FROM public.messages
    WHERE id = p_original_message_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Original message not found.';
    END IF;

    SELECT sender_id::text, receiver_id::text, created_at
    INTO v_reply_sender_id, v_reply_receiver_id, v_reply_created
    FROM public.messages
    WHERE id = p_reply_message_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Reply message not found.';
    END IF;

    -- 6. Verify responder is author of reply message
    IF v_reply_sender_id <> v_responder_id THEN
        RAISE EXCEPTION 'Responder is not the author of the reply message.';
    END IF;

    -- 7. Verify messages belong to matching bidirectional conversation pair
    IF v_orig_receiver_id <> v_responder_id OR v_reply_receiver_id <> v_orig_sender_id THEN
        RAISE EXCEPTION 'Messages do not belong to matching conversation participants.';
    END IF;

    -- 8. Verify original sender is male
    SELECT gender INTO v_orig_sender_gender FROM public.profiles WHERE id::text = v_orig_sender_id;
    IF LOWER(COALESCE(v_orig_sender_gender, '')) NOT IN ('male', 'man', 'm') THEN
        RETURN jsonb_build_object(
            'success', false,
            'eligible', false,
            'error', 'Original sender must be male to qualify for fast reply reward.',
            'diamonds_awarded', 0.00
        );
    END IF;

    -- 9. Calculate response seconds strictly from trusted PostgreSQL message timestamps
    v_response_seconds := EXTRACT(EPOCH FROM (v_reply_created - v_orig_created));

    IF v_response_seconds < 0 THEN
        RAISE EXCEPTION 'Invalid response time: Reply timestamp precedes original message.';
    END IF;

    IF v_response_seconds >= 180.0 THEN
        RETURN jsonb_build_object(
            'success', true,
            'eligible', false,
            'status', 'EXPIRED',
            'response_seconds', ROUND(v_response_seconds, 2),
            'diamonds_awarded', 0.00,
            'message', 'Reply time exceeded 180 seconds window.'
        );
    END IF;

    -- 10. Proportional calculation formula:
    -- reward = 20 * (180 - response_seconds) / 180
    v_reward := ROUND((20.0 * (180.0 - v_response_seconds) / 180.0)::numeric, 2);

    IF v_reward > 20.00 THEN
        v_reward := 20.00;
    ELSIF v_reward < 0.00 THEN
        v_reward := 0.00;
    END IF;

    -- 11. Lock female wallet row and credit diamonds
    SELECT diamonds INTO v_new_diamonds
    FROM public.profiles
    WHERE id::text = v_responder_id
    FOR UPDATE;

    UPDATE public.profiles
    SET diamonds = COALESCE(diamonds, 0.00) + v_reward,
        updated_at = now()
    WHERE id::text = v_responder_id
    RETURNING diamonds INTO v_new_diamonds;

    -- 12. Create audit records in ledger
    INSERT INTO public.fast_reply_transactions (
        idempotency_key,
        female_user_id,
        male_user_id,
        original_message_id,
        reply_message_id,
        response_seconds,
        diamonds_awarded,
        created_at
    ) VALUES (
        p_idempotency_key,
        v_responder_id,
        v_orig_sender_id,
        p_original_message_id,
        p_reply_message_id,
        ROUND(v_response_seconds, 2),
        v_reward,
        now()
    );

    INSERT INTO public.diamond_transactions (
        user_id,
        amount,
        type,
        transaction_type,
        title,
        description,
        reference_id,
        status,
        created_at
    ) VALUES (
        v_responder_id,
        v_reward,
        'FAST_REPLY_REWARD',
        'FAST_REPLY_REWARD',
        'Fast Reply Reward',
        'Earned ' || v_reward || ' 💎 for replying in ' || ROUND(v_response_seconds, 1) || 's to male user',
        p_idempotency_key,
        'Completed',
        now()
    );

    -- 13. Return verified response
    RETURN jsonb_build_object(
        'success', true,
        'eligible', true,
        'duplicate', false,
        'idempotency_key', p_idempotency_key,
        'female_user_id', v_responder_id,
        'male_user_id', v_orig_sender_id,
        'response_seconds', ROUND(v_response_seconds, 2),
        'diamonds_awarded', v_reward,
        'new_diamonds', v_new_diamonds
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION public.process_fast_reply_reward(BIGINT, BIGINT, TEXT, TEXT) TO authenticated, service_role;

-- 11. Refresh schema cache
NOTIFY pgrst, 'reload schema';
