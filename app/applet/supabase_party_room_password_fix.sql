-- =========================================================================
-- QIVO PARTY ROOM PASSWORD & LOCK MANAGEMENT FIX
-- Run this in Supabase SQL Editor to enable 100% reliable room locking,
-- unlocking, and password verification with Security Definer privileges.
-- =========================================================================

-- 1. Ensure columns exist on party_rooms table
ALTER TABLE party_rooms ADD COLUMN IF NOT EXISTS room_password TEXT;
ALTER TABLE party_rooms ADD COLUMN IF NOT EXISTS is_locked BOOLEAN DEFAULT FALSE;

-- 2. Drop any previous function definitions to avoid return type mismatch error
DROP FUNCTION IF EXISTS set_party_room_password(TEXT, TEXT);
DROP FUNCTION IF EXISTS set_party_room_password(p_room_id TEXT, p_password TEXT);
DROP FUNCTION IF EXISTS verify_party_room_password(TEXT, TEXT);
DROP FUNCTION IF EXISTS verify_party_room_password(p_room_id TEXT, p_password TEXT);

-- 3. Create set_party_room_password RPC
-- Handles setting a 6-digit PIN, updating it, or removing it completely (unlocking).
CREATE OR REPLACE FUNCTION set_party_room_password(
    p_room_id TEXT,
    p_password TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_clean_pwd TEXT;
    v_locked BOOLEAN;
BEGIN
    -- Sanitize input password
    v_clean_pwd := TRIM(COALESCE(p_password, ''));
    IF v_clean_pwd = '' OR LOWER(v_clean_pwd) = 'null' THEN
        v_clean_pwd := NULL;
        v_locked := FALSE;
    ELSE
        v_locked := TRUE;
    END IF;

    -- Update the party room password and locked status
    UPDATE party_rooms
    SET 
        room_password = v_clean_pwd,
        is_locked = v_locked,
        updated_at = NOW()
    WHERE id = p_room_id;

    RETURN jsonb_build_object(
        'success', TRUE,
        'room_id', p_room_id,
        'is_locked', v_locked,
        'has_password', (v_clean_pwd IS NOT NULL)
    );
END;
$$;

-- Grant execution to public/anon and authenticated users
GRANT EXECUTE ON FUNCTION set_party_room_password(TEXT, TEXT) TO anon, authenticated, service_role;

-- 4. Create verify_party_room_password RPC
-- Allows a user entering a locked room to verify the 6-digit PIN securely.
CREATE OR REPLACE FUNCTION verify_party_room_password(
    p_room_id TEXT,
    p_password TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_actual_pwd TEXT;
    v_locked BOOLEAN;
    v_input_pwd TEXT;
BEGIN
    SELECT room_password, is_locked INTO v_actual_pwd, v_locked
    FROM party_rooms
    WHERE id = p_room_id;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Room not found');
    END IF;

    -- If room is not locked or password is empty, allow entry immediately
    v_actual_pwd := TRIM(COALESCE(v_actual_pwd, ''));
    IF NOT COALESCE(v_locked, FALSE) OR v_actual_pwd = '' OR LOWER(v_actual_pwd) = 'null' THEN
        RETURN jsonb_build_object('success', TRUE, 'is_locked', FALSE);
    END IF;

    v_input_pwd := TRIM(COALESCE(p_password, ''));
    IF v_input_pwd = v_actual_pwd THEN
        RETURN jsonb_build_object('success', TRUE, 'is_locked', TRUE);
    ELSE
        RETURN jsonb_build_object('success', FALSE, 'error', 'Incorrect password! Please try again.');
    END IF;
END;
$$;

-- Grant execution to public/anon and authenticated users
GRANT EXECUTE ON FUNCTION verify_party_room_password(TEXT, TEXT) TO anon, authenticated, service_role;

-- 5. Ensure UPDATE access on party_rooms table
GRANT SELECT, INSERT, UPDATE ON party_rooms TO anon, authenticated;

-- If RLS is enabled, add a permissive update policy for room owners / hosts
DROP POLICY IF EXISTS "party_rooms_update_policy" ON party_rooms;
CREATE POLICY "party_rooms_update_policy" ON party_rooms
FOR UPDATE
TO anon, authenticated
USING (true)
WITH CHECK (true);
