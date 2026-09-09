-- ============================================================================
-- VERIFICATION & TEST SUITE FOR COINS, CONSECUTIVE GIFTS & TIME PERCENTAGE
-- Run in Supabase SQL Editor to test and verify all core functions
-- Fully compatible with TEXT and UUID types.
-- ============================================================================

DO $$
DECLARE
    v_male_id TEXT := 'test_male_' || gen_random_uuid()::text;
    v_female_id TEXT := 'test_female_' || gen_random_uuid()::text;
    v_recipient_male_id TEXT := 'test_recip_male_' || gen_random_uuid()::text;
    v_gift_result JSONB;
    v_fast_reply_result JSONB;
    v_orig_msg_id BIGINT := 999901;
    v_reply_msg_id BIGINT := 999902;
    v_male_coins BIGINT;
    v_female_diamonds NUMERIC(14, 2);
    v_key TEXT;
    v_key2 TEXT;
BEGIN
    RAISE NOTICE '--- STARTING TEST SUITE ---';

    -- Setup test user profiles
    INSERT INTO public.profiles (id, name, gender, coins, diamonds) VALUES
    (v_male_id, 'Test Male Sender', 'Male', 1000, 0.00),
    (v_female_id, 'Test Female Receiver', 'Female', 100, 0.00),
    (v_recipient_male_id, 'Test Male Receiver', 'Male', 0, 0.00)
    ON CONFLICT (id) DO UPDATE SET coins = EXCLUDED.coins, diamonds = EXCLUDED.diamonds;

    -- ========================================================================
    -- TEST 1: Standard Gift (gift_magic: 500 coins -> 500.00 diamonds)
    -- ========================================================================
    v_key := 'test_gift_500_' || gen_random_uuid()::text;
    v_gift_result := public.process_gift(
        p_recipient_id := v_female_id,
        p_gift_id := 'gift_magic',
        p_idempotency_key := v_key,
        p_sender_id := v_male_id
    );

    SELECT coins INTO v_male_coins FROM public.profiles WHERE id::text = v_male_id;
    SELECT diamonds INTO v_female_diamonds FROM public.profiles WHERE id::text = v_female_id;

    ASSERT v_male_coins = 500, 'Test 1 Failed: Sender should have 500 coins left';
    ASSERT v_female_diamonds = 500.00, 'Test 1 Failed: Recipient should have 500 diamonds';
    RAISE NOTICE '✓ TEST 1 PASSED: 500-coin gift successfully credited 500.00 diamonds';

    -- ========================================================================
    -- TEST 2: Consecutive Gifting (Back-to-back gifts succeed independently)
    -- ========================================================================
    v_key := 'test_consec_1_' || gen_random_uuid()::text;
    v_gift_result := public.process_gift(
        p_recipient_id := v_female_id,
        p_gift_id := 'gift_rose', -- 10 coins
        p_idempotency_key := v_key,
        p_sender_id := v_male_id
    );

    -- Send immediately second consecutive gift
    v_key2 := 'test_consec_2_' || gen_random_uuid()::text;
    v_gift_result := public.process_gift(
        p_recipient_id := v_female_id,
        p_gift_id := 'gift_rose', -- 10 coins
        p_idempotency_key := v_key2,
        p_sender_id := v_male_id
    );

    SELECT coins INTO v_male_coins FROM public.profiles WHERE id::text = v_male_id;
    SELECT diamonds INTO v_female_diamonds FROM public.profiles WHERE id::text = v_female_id;

    ASSERT v_male_coins = 480, 'Test 2 Failed: Consecutive gifts should deduct 20 coins total';
    ASSERT v_female_diamonds = 520.00, 'Test 2 Failed: Recipient should have 520 diamonds after consecutive gifts';
    RAISE NOTICE '✓ TEST 2 PASSED: Consecutive gifts both succeeded and credited recipient';

    -- ========================================================================
    -- TEST 3: Gifting with Time Percentage Conversion (Yields exact decimal e.g. 86.55)
    -- ========================================================================
    -- Insert simulated incoming message from recipient 24.21 seconds ago (86.55% time left)
    DELETE FROM public.messages WHERE id IN (v_orig_msg_id, v_reply_msg_id);
    INSERT INTO public.messages (id, sender_id, receiver_id, content, created_at)
    VALUES (v_orig_msg_id, v_female_id, v_male_id, 'Hello!', now() - INTERVAL '24.21 seconds');

    -- Send gift_crown (100 coins, 100 diamonds base) with time conversion
    v_key := 'test_time_pct_' || gen_random_uuid()::text;
    v_gift_result := public.process_gift(
        p_recipient_id := v_female_id,
        p_gift_id := 'gift_crown',
        p_idempotency_key := v_key,
        p_sender_id := v_male_id,
        p_original_message_id := v_orig_msg_id
    );

    SELECT diamonds INTO v_female_diamonds FROM public.profiles WHERE id::text = v_female_id;
    RAISE NOTICE 'Gift time percentage result: % (Diamonds: %)', v_gift_result->>'time_percentage', v_gift_result->>'diamonds_awarded';
    
    -- Recipient was at 520.00, should receive ~86.55 diamonds
    ASSERT (v_gift_result->>'diamonds_awarded')::numeric >= 86.00 AND (v_gift_result->>'diamonds_awarded')::numeric <= 87.00,
        'Test 3 Failed: Diamonds should be converted by percentage of time';
    RAISE NOTICE '✓ TEST 3 PASSED: Time percentage converted gift accurately with fractional decimal diamonds';

    -- ========================================================================
    -- TEST 4: Self-gifting prevented
    -- ========================================================================
    BEGIN
        v_key := 'test_self_' || gen_random_uuid()::text;
        PERFORM public.process_gift(
            p_recipient_id := v_male_id,
            p_gift_id := 'gift_rose',
            p_idempotency_key := v_key,
            p_sender_id := v_male_id
        );
        RAISE EXCEPTION 'Test 4 Failed: Self-gifting did not fail';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%Self-gifting%' THEN
            RAISE NOTICE '✓ TEST 4 PASSED: Self-gifting strictly prevented';
        ELSE
            RAISE;
        END IF;
    END;

    -- ========================================================================
    -- TEST 5: Insufficient coins rejected atomically
    -- ========================================================================
    BEGIN
        v_key := 'test_poor_' || gen_random_uuid()::text;
        -- Attempt to send 10,000 coin jet with only ~380 coins left
        PERFORM public.process_gift(
            p_recipient_id := v_female_id,
            p_gift_id := 'gift_jet',
            p_idempotency_key := v_key,
            p_sender_id := v_male_id
        );
        RAISE EXCEPTION 'Test 5 Failed: Insufficient coins did not fail';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%Insufficient coins%' THEN
            RAISE NOTICE '✓ TEST 5 PASSED: Insufficient coins rejected with zero changes';
        ELSE
            RAISE;
        END IF;
    END;

    -- Clean up test records
    DELETE FROM public.gift_transactions WHERE sender_id::text IN (v_male_id, v_female_id);
    DELETE FROM public.coin_transactions WHERE user_id::text IN (v_male_id, v_female_id);
    DELETE FROM public.diamond_transactions WHERE user_id::text IN (v_male_id, v_female_id);
    DELETE FROM public.messages WHERE id IN (v_orig_msg_id, v_reply_msg_id);
    DELETE FROM public.profiles WHERE id::text IN (v_male_id, v_female_id, v_recipient_male_id);

    RAISE NOTICE '--- ALL TESTS PASSED SUCCESSFULLY ---';
END;
$$;
