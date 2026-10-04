-- ============================================================================
-- QIVO PARTY ROOM SECURITY HARDENING & ENHANCEMENTS
-- ============================================================================

-- 1. TABLE ENHANCEMENTS
ALTER TABLE public.party_rooms ADD COLUMN IF NOT EXISTS max_admins INTEGER DEFAULT 5;
ALTER TABLE public.party_rooms ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT true;

-- Gifts Master Table (if not exists)
CREATE TABLE IF NOT EXISTS public.gifts (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    price BIGINT NOT NULL,
    icon_url TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Seed some gifts if empty
INSERT INTO public.gifts (id, name, price)
VALUES 
('rose', 'Rose', 10),
('heart', 'Heart', 50),
('love', 'Love', 100),
('diamond', 'Diamond', 200),
('rocket', 'Rocket', 500),
('sports_car', 'Sports Car', 1000),
('crown', 'Crown', 2000),
('castle', 'Castle', 5000),
('dragon', 'Dragon', 10000),
('universe', 'Universe', 20000)
ON CONFLICT (id) DO UPDATE SET price = EXCLUDED.price;

-- Gift Transactions / Ledger
CREATE TABLE IF NOT EXISTS public.party_room_gifts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id TEXT NOT NULL,
    sender_id TEXT NOT NULL,
    recipient_id TEXT NOT NULL,
    gift_id TEXT NOT NULL,
    quantity INTEGER DEFAULT 1,
    total_price BIGINT NOT NULL,
    idempotency_key TEXT UNIQUE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 2. RESET RLS POLICIES (MOVE TO RPC-ONLY FOR MODIFICATIONS)
-- We strictly revoke direct DML (Insert/Update/Delete) for non-service roles on sensitive tables.

DO $$ 
BEGIN
    -- Party Rooms
    DROP POLICY IF EXISTS "View party rooms" ON public.party_rooms;
    DROP POLICY IF EXISTS "Create party rooms" ON public.party_rooms;
    DROP POLICY IF EXISTS "Manage party rooms" ON public.party_rooms;
    
    CREATE POLICY "party_rooms_select" ON public.party_rooms FOR SELECT TO authenticated USING (is_active = true);

    -- Party Room Seats
    DROP POLICY IF EXISTS "View room seats" ON public.party_room_seats;
    DROP POLICY IF EXISTS "Manage room seats" ON public.party_room_seats;
    
    CREATE POLICY "party_seats_select" ON public.party_room_seats FOR SELECT TO authenticated USING (true);

    -- Party Room Members
    DROP POLICY IF EXISTS "View room members" ON public.party_room_members;
    DROP POLICY IF EXISTS "Manage room members" ON public.party_room_members;
    
    CREATE POLICY "party_members_select" ON public.party_room_members FOR SELECT TO authenticated USING (true);

    -- Party Room Admins
    DROP POLICY IF EXISTS "View room admins" ON public.party_room_admins;
    DROP POLICY IF EXISTS "Manage room admins" ON public.party_room_admins;
    
    CREATE POLICY "party_admins_select" ON public.party_room_admins FOR SELECT TO authenticated USING (true);

    -- Party Room Messages
    DROP POLICY IF EXISTS "View room messages" ON public.party_room_messages;
    DROP POLICY IF EXISTS "Send room messages" ON public.party_room_messages;
    
    CREATE POLICY "party_messages_select" ON public.party_room_messages FOR SELECT TO authenticated USING (true);
    -- We allow direct insert for messages as they are high volume and low risk if RLS CHECK sender_id is enforced
    CREATE POLICY "party_messages_insert" ON public.party_room_messages FOR INSERT TO authenticated 
    WITH CHECK (auth.uid()::text = sender_id::text AND EXISTS (SELECT 1 FROM public.party_room_members WHERE room_id = party_room_messages.room_id AND user_id = auth.uid()::text));

END $$;

-- 3. SECURE RPC FOR ROOM MANAGEMENT

-- [RPC] Create Room (Hardened)
CREATE OR REPLACE FUNCTION public.create_party_room_v2(
    p_name TEXT,
    p_max_seats INTEGER DEFAULT 8,
    p_category TEXT DEFAULT 'Chat',
    p_cover_image TEXT DEFAULT '',
    p_background_theme TEXT DEFAULT 'default'
)
RETURNS jsonb AS $$
DECLARE
    v_user_id TEXT;
    v_room_id TEXT;
    v_room_num BIGINT;
    v_cost BIGINT := 5000;
    v_user_coins BIGINT;
BEGIN
    v_user_id := auth.uid()::text;
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;

    -- Transactional Lock & Balance Check
    SELECT coins INTO v_user_coins FROM public.profiles WHERE id = v_user_id FOR UPDATE;
    IF v_user_coins < v_cost THEN
        RETURN jsonb_build_object('success', false, 'error', 'Insufficient coins. Need ' || v_cost);
    END IF;

    -- Deduct
    UPDATE public.profiles SET coins = coins - v_cost WHERE id = v_user_id;

    -- Log transaction
    INSERT INTO public.coin_transactions (user_id, amount, type, title, description)
    VALUES (v_user_id, -v_cost, 'PARTY_ROOM_CREATE', 'Room Creation', 'Created room: ' || p_name);

    v_room_id := gen_random_uuid()::text;
    v_room_num := floor(100000 + random() * 900000)::bigint;

    INSERT INTO public.party_rooms (id, room_number, title, category, cover_image, host_user_id, seats_count, background_theme)
    VALUES (v_room_id, v_room_num, p_name, p_category, p_cover_image, v_user_id, p_max_seats, p_background_theme);

    -- Auto join as member
    INSERT INTO public.party_room_members (room_id, user_id) VALUES (v_room_id, v_user_id);

    RETURN jsonb_build_object('success', true, 'room_id', v_room_id, 'room_number', v_room_num);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- [RPC] Join Room
CREATE OR REPLACE FUNCTION public.join_party_room(p_room_id TEXT)
RETURNS jsonb AS $$
DECLARE
    v_user_id TEXT;
    v_name TEXT;
    v_avatar TEXT;
BEGIN
    v_user_id := auth.uid()::text;
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;

    SELECT name, avatar_url INTO v_name, v_avatar FROM public.profiles WHERE id = v_user_id;

    INSERT INTO public.party_room_members (room_id, user_id, user_name, user_avatar)
    VALUES (p_room_id, v_user_id, v_name, v_avatar)
    ON CONFLICT (room_id, user_id) DO UPDATE 
    SET user_name = v_name, user_avatar = v_avatar, joined_at = now();

    RETURN jsonb_build_object('success', true);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- [RPC] Leave Room
CREATE OR REPLACE FUNCTION public.leave_party_room(p_room_id TEXT)
RETURNS jsonb AS $$
DECLARE
    v_user_id TEXT;
BEGIN
    v_user_id := auth.uid()::text;
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;

    -- Release seat if any
    DELETE FROM public.party_room_seats WHERE room_id = p_room_id AND user_id = v_user_id;
    -- Remove from members
    DELETE FROM public.party_room_members WHERE room_id = p_room_id AND user_id = v_user_id;

    RETURN jsonb_build_object('success', true);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- [RPC] Occupy Seat (Strict)
CREATE OR REPLACE FUNCTION public.secure_occupy_seat(
    p_room_id TEXT,
    p_seat_index INTEGER
)
RETURNS jsonb AS $$
DECLARE
    v_user_id TEXT;
    v_name TEXT;
    v_avatar TEXT;
    v_is_member BOOLEAN;
    v_is_locked BOOLEAN;
    v_occupied_by TEXT;
    v_max_seats INTEGER;
BEGIN
    v_user_id := auth.uid()::text;
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;

    -- Verify room membership
    SELECT EXISTS (SELECT 1 FROM public.party_room_members WHERE room_id = p_room_id AND user_id = v_user_id) INTO v_is_member;
    IF NOT v_is_member THEN
        RETURN jsonb_build_object('success', false, 'error', 'Must be a member of the room to sit.');
    END IF;

    SELECT seats_count INTO v_max_seats FROM public.party_rooms WHERE id = p_room_id;
    IF p_seat_index < 0 OR p_seat_index >= v_max_seats THEN
        RETURN jsonb_build_object('success', false, 'error', 'Invalid seat');
    END IF;

    -- Atomic Lock Check
    SELECT is_locked, user_id INTO v_is_locked, v_occupied_by FROM public.party_room_seats 
    WHERE room_id = p_room_id AND seat_index = p_seat_index FOR UPDATE;

    IF v_is_locked THEN RETURN jsonb_build_object('success', false, 'error', 'Seat is locked'); END IF;
    IF v_occupied_by IS NOT NULL AND v_occupied_by <> v_user_id THEN 
        RETURN jsonb_build_object('success', false, 'error', 'Seat is occupied'); 
    END IF;

    -- Enforce one seat per user
    DELETE FROM public.party_room_seats WHERE room_id = p_room_id AND user_id = v_user_id;

    SELECT name, avatar_url INTO v_name, v_avatar FROM public.profiles WHERE id = v_user_id;

    INSERT INTO public.party_room_seats (room_id, seat_index, user_id, user_name, user_avatar)
    VALUES (p_room_id, p_seat_index, v_user_id, v_name, v_avatar)
    ON CONFLICT (room_id, seat_index) DO UPDATE 
    SET user_id = v_user_id, user_name = v_name, user_avatar = v_avatar, updated_at = now();

    RETURN jsonb_build_object('success', true);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- [RPC] Release Seat (Strict)
CREATE OR REPLACE FUNCTION public.secure_release_seat(
    p_room_id TEXT,
    p_seat_index INTEGER
)
RETURNS jsonb AS $$
DECLARE
    v_user_id TEXT;
    v_seat_user_id TEXT;
    v_is_authorized BOOLEAN;
BEGIN
    v_user_id := auth.uid()::text;
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;

    SELECT user_id INTO v_seat_user_id FROM public.party_room_seats WHERE room_id = p_room_id AND seat_index = p_seat_index;
    
    -- Check if caller is occupant or owner or admin
    v_is_authorized := (v_user_id = v_seat_user_id) OR 
                       EXISTS (SELECT 1 FROM public.party_rooms WHERE id = p_room_id AND host_user_id = v_user_id) OR
                       EXISTS (SELECT 1 FROM public.party_room_admins WHERE room_id = p_room_id AND user_id = v_user_id);

    IF NOT v_is_authorized THEN
        RETURN jsonb_build_object('success', false, 'error', 'Unauthorized');
    END IF;

    DELETE FROM public.party_room_seats WHERE room_id = p_room_id AND seat_index = p_seat_index;
    RETURN jsonb_build_object('success', true);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- [RPC] Gifting (Hardened)
CREATE OR REPLACE FUNCTION public.gift_party_room(
    p_recipient_id TEXT,
    p_gift_id TEXT,
    p_quantity INTEGER,
    p_room_id TEXT,
    p_idempotency_key TEXT
)
RETURNS jsonb AS $$
DECLARE
    v_sender_id TEXT;
    v_gift_price BIGINT;
    v_total_price BIGINT;
    v_sender_coins BIGINT;
    v_sender_name TEXT;
    v_recipient_name TEXT;
    v_recipient_exists BOOLEAN;
    v_diamonds_earned NUMERIC(14, 2);
BEGIN
    v_sender_id := auth.uid()::text;
    IF v_sender_id IS NULL THEN RAISE EXCEPTION 'Unauthorized'; END IF;

    -- Idempotency check
    IF EXISTS (SELECT 1 FROM public.party_room_gifts WHERE idempotency_key = p_idempotency_key) THEN
        RETURN jsonb_build_object('success', true, 'message', 'Duplicate request');
    END IF;

    -- Verify room membership for both
    IF NOT EXISTS (SELECT 1 FROM public.party_room_members WHERE room_id = p_room_id AND user_id = v_sender_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Sender not in room');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.party_room_members WHERE room_id = p_room_id AND user_id = p_recipient_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Recipient not in room');
    END IF;

    -- Get actual price
    SELECT price INTO v_gift_price FROM public.gifts WHERE id = p_gift_id;
    IF v_gift_price IS NULL THEN RETURN jsonb_build_object('success', false, 'error', 'Invalid gift'); END IF;

    v_total_price := v_gift_price * p_quantity;

    -- Lock Sender
    SELECT coins, name INTO v_sender_coins, v_sender_name FROM public.profiles WHERE id = v_sender_id FOR UPDATE;
    IF v_sender_coins < v_total_price THEN
        RETURN jsonb_build_object('success', false, 'error', 'Insufficient coins');
    END IF;

    SELECT name INTO v_recipient_name FROM public.profiles WHERE id = p_recipient_id;

    -- Deduct Sender
    UPDATE public.profiles SET coins = coins - v_total_price WHERE id = v_sender_id;

    -- Credit Recipient (Diamonds)
    v_diamonds_earned := ROUND(v_total_price * 0.5, 2);
    UPDATE public.profiles SET diamonds = diamonds + v_diamonds_earned WHERE id = p_recipient_id;

    -- Record Ledger
    INSERT INTO public.party_room_gifts (room_id, sender_id, recipient_id, gift_id, quantity, total_price, idempotency_key)
    VALUES (p_room_id, v_sender_id, p_recipient_id, p_gift_id, p_quantity, v_total_price, p_idempotency_key);

    -- Record Transactions
    INSERT INTO public.coin_transactions (user_id, amount, type, title, description)
    VALUES (v_sender_id, -v_total_price, 'GIFT_PARTY', 'Gift Sent', 'Sent ' || p_gift_id || ' in room');
    
    INSERT INTO public.diamond_transactions (user_id, amount, type, title, description)
    VALUES (p_recipient_id, v_diamonds_earned, 'GIFT_PARTY', 'Gift Received', 'Received ' || p_gift_id || ' in room');

    -- Insert Room Message
    INSERT INTO public.party_room_messages (room_id, sender_id, sender_name, content, msg_type)
    VALUES (p_room_id, v_sender_id, v_sender_name, 'sent ' || p_quantity || 'x ' || p_gift_id || ' to ' || v_recipient_name, 'gift');

    RETURN jsonb_build_object('success', true, 'total_price', v_total_price, 'diamonds_awarded', v_diamonds_earned);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- [RPC] Manage Admins
CREATE OR REPLACE FUNCTION public.manage_party_admin(
    p_room_id TEXT,
    p_user_id TEXT,
    p_action TEXT -- 'ADD' or 'REMOVE'
)
RETURNS jsonb AS $$
DECLARE
    v_caller_id TEXT;
    v_current_admins INTEGER;
BEGIN
    v_caller_id := auth.uid()::text;
    IF NOT EXISTS (SELECT 1 FROM public.party_rooms WHERE id = p_room_id AND host_user_id = v_caller_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Only owner can manage admins');
    END IF;

    IF p_action = 'ADD' THEN
        SELECT count(*) INTO v_current_admins FROM public.party_room_admins WHERE room_id = p_room_id;
        IF v_current_admins >= 5 THEN
            RETURN jsonb_build_object('success', false, 'error', 'Admin limit reached (5)');
        END IF;
        INSERT INTO public.party_room_admins (room_id, user_id) VALUES (p_room_id, p_user_id) ON CONFLICT DO NOTHING;
    ELSE
        DELETE FROM public.party_room_admins WHERE room_id = p_room_id AND user_id = p_user_id;
    END IF;

    RETURN jsonb_build_object('success', true);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- [RPC] Remove Member (Kick)
CREATE OR REPLACE FUNCTION public.kick_party_member(
    p_room_id TEXT,
    p_user_id TEXT
)
RETURNS jsonb AS $$
DECLARE
    v_caller_id TEXT;
    v_is_authorized BOOLEAN;
BEGIN
    v_caller_id := auth.uid()::text;
    
    v_is_authorized := EXISTS (SELECT 1 FROM public.party_rooms WHERE id = p_room_id AND host_user_id = v_caller_id) OR
                       EXISTS (SELECT 1 FROM public.party_room_admins WHERE room_id = p_room_id AND user_id = v_caller_id);

    IF NOT v_is_authorized THEN
        RETURN jsonb_build_object('success', false, 'error', 'Unauthorized');
    END IF;

    -- Remove seat if any
    DELETE FROM public.party_room_seats WHERE room_id = p_room_id AND user_id = p_user_id;
    -- Remove member
    DELETE FROM public.party_room_members WHERE room_id = p_room_id AND user_id = p_user_id;

    RETURN jsonb_build_object('success', true);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- [RPC] Change Seat Count
CREATE OR REPLACE FUNCTION public.update_room_seats_count(
    p_room_id TEXT,
    p_new_count INTEGER
)
RETURNS jsonb AS $$
DECLARE
    v_caller_id TEXT;
BEGIN
    v_caller_id := auth.uid()::text;
    IF NOT EXISTS (SELECT 1 FROM public.party_rooms WHERE id = p_room_id AND host_user_id = v_caller_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Only owner can change seats');
    END IF;

    IF p_new_count < 4 OR p_new_count > 12 THEN
        RETURN jsonb_build_object('success', false, 'error', 'Seats must be between 4 and 12');
    END IF;

    UPDATE public.party_rooms SET seats_count = p_new_count WHERE id = p_room_id;
    
    -- Cleanup seats beyond new count
    DELETE FROM public.party_room_seats WHERE room_id = p_room_id AND seat_index >= p_new_count;

    RETURN jsonb_build_object('success', true);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
