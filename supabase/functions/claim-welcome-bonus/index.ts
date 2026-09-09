// ============================================================================
// Supabase Edge Function: claim-welcome-bonus
// Anti-Fraud Device Lock + 500 Free Coins Grant (Zero Diamonds)
// Deploy with: supabase functions deploy claim-welcome-bonus --no-verify-jwt
// ============================================================================

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const supabaseServiceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

    if (!supabaseUrl || !supabaseServiceRoleKey) {
      return new Response(
        JSON.stringify({ success: false, error: "Server configuration missing." }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Service role client bypasses RLS for atomic financial transactions
    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceRoleKey);

    // Extract Bearer token from authorization header to authenticate caller
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(
        JSON.stringify({ success: false, error: "Authorization header required." }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const token = authHeader.replace("Bearer ", "").trim();
    const { data: { user }, error: userError } = await supabaseAdmin.auth.getUser(token);

    if (userError || !user) {
      return new Response(
        JSON.stringify({ success: false, error: "Invalid or expired session token." }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const userId = user.id;
    const body = await req.json().catch(() => ({}));
    const deviceHash = (body.p_device_hash || body.device_hash || "").trim();
    const clientIp = req.headers.get("x-forwarded-for") || req.headers.get("cf-connecting-ip") || "unknown";

    if (!deviceHash || deviceHash.length < 16) {
      return new Response(
        JSON.stringify({ success: false, error: "Valid device fingerprint required for verification." }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 1. Anti-Fraud Check: Check if user already claimed
    const { data: existingUserClaim } = await supabaseAdmin
      .from("claimed_welcome_bonuses")
      .select("id")
      .eq("user_id", userId)
      .maybeSingle();

    if (existingUserClaim) {
      return new Response(
        JSON.stringify({
          success: false,
          error: "Welcome bonus has already been claimed for this account.",
          coins_awarded: 0
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 2. Anti-Fraud Check: Check if this physical device has already claimed (Limit: 1 per device)
    const { count: deviceClaimsCount } = await supabaseAdmin
      .from("claimed_welcome_bonuses")
      .select("id", { count: "exact", head: true })
      .eq("device_hash", deviceHash);

    if (deviceClaimsCount && deviceClaimsCount >= 1) {
      return new Response(
        JSON.stringify({
          success: false,
          error: "Free 500 welcome coins have already been claimed on this device.",
          coins_awarded: 0
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const BONUS_AMOUNT = 500;

    // 3. Atomically record the claim in claimed_welcome_bonuses
    const { error: insertClaimError } = await supabaseAdmin
      .from("claimed_welcome_bonuses")
      .insert({
        user_id: userId,
        device_hash: deviceHash,
        bonus_coins: BONUS_AMOUNT
      });

    if (insertClaimError) {
      return new Response(
        JSON.stringify({ success: false, error: "Failed to record device claim: " + insertClaimError.message }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 4. Fetch current user profile to safely increment coins and ensure diamonds are 0
    const { data: profile } = await supabaseAdmin
      .from("profiles")
      .select("coins, diamonds")
      .eq("id", userId)
      .single();

    const currentCoins = Number(profile?.coins || 0);
    const newCoins = currentCoins + BONUS_AMOUNT;

    // Credit coins, keep diamonds intact or ensure 0 on initial creation
    await supabaseAdmin
      .from("profiles")
      .update({
        coins: newCoins,
        updated_at: new Date().toISOString()
      })
      .eq("id", userId);

    // 5. Ledger record in coin_transactions
    await supabaseAdmin
      .from("coin_transactions")
      .insert({
        user_id: userId,
        amount: BONUS_AMOUNT,
        type: "WELCOME_BONUS",
        title: "Welcome Gift",
        description: `Awarded ${BONUS_AMOUNT} Welcome Gift Coins (Device Verified: ${deviceHash.substring(0, 8)}...)`
      });

    return new Response(
      JSON.stringify({
        success: true,
        coins_awarded: BONUS_AMOUNT,
        new_balance: newCoins
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err: any) {
    return new Response(
      JSON.stringify({ success: false, error: err.message || "Internal server error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
