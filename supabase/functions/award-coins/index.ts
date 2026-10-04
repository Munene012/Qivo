// ============================================================================
// Supabase Edge Function: award-coins
// Secure Server-Side Coin Award & P2P Transfer Engine for QIVO
//
// SECURITY HARDENING:
// - Never trusts client-provided sender_id, is_admin, or is_coinseller.
// - Authoritatively derives sender identity from verified JWT (auth.uid()).
// - Performs database lookups to verify caller's actual roles (is_admin, is_coinseller).
// - Calls the restricted 'award_coins' RPC using SERVICE_ROLE.
//
// Deploy with Supabase CLI:
//   supabase functions deploy award-coins --verify-jwt
// ============================================================================

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

interface AwardRequest {
  target_numeric_id: number;
  amount: number;
  reason?: string;
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const supabaseServiceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

    if (!supabaseUrl || !supabaseServiceRoleKey) {
      console.error("Missing Supabase configuration");
      return new Response(
        JSON.stringify({ success: false, error: "CONFIGURATION_ERROR" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 1. Authoritative Supabase Client (Internal Use Only)
    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceRoleKey);

    // 2. Identify Caller via JWT (Mandatory)
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(
        JSON.stringify({ success: false, error: "UNAUTHORIZED", message: "Missing Authorization header" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const { data: { user }, error: authError } = await supabaseAdmin.auth.getUser(authHeader.replace("Bearer ", ""));
    if (authError || !user) {
      return new Response(
        JSON.stringify({ success: false, error: "UNAUTHORIZED", message: "Invalid or expired session" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const callerId = user.id;

    // 3. Authoritative Role Verification from Database
    const { data: callerProfile, error: profileError } = await supabaseAdmin
      .from("profiles")
      .select("id, numeric_id, is_admin, is_coinseller, coins")
      .eq("id", callerId)
      .single();

    if (profileError || !callerProfile) {
      return new Response(
        JSON.stringify({ success: false, error: "SENDER_NOT_FOUND", message: "Your profile could not be verified" }),
        { status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const isAdmin = callerProfile.is_admin === true;
    const isCoinSeller = callerProfile.is_coinseller === true;

    if (!isAdmin && !isCoinSeller) {
      return new Response(
        JSON.stringify({ success: false, error: "UNAUTHORIZED_ROLE", message: "Only Admins and Coin Sellers can award coins" }),
        { status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 4. Validate Payload
    const payload: AwardRequest = await req.json().catch(() => ({}));
    const targetNumericId = Math.floor(Number(payload.target_numeric_id || 0));
    const amount = Math.floor(Number(payload.amount || 0));
    const reason = (payload.reason || "").trim();

    if (amount <= 0) {
      return new Response(
        JSON.stringify({ success: false, error: "INVALID_AMOUNT", message: "Amount must be greater than 0" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    if (amount > 1000000) {
       return new Response(
        JSON.stringify({ success: false, error: "INVALID_AMOUNT", message: "Max award limit is 1,000,000 coins" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    if (targetNumericId <= 0) {
      return new Response(
        JSON.stringify({ success: false, error: "MISSING_TARGET", message: "Recipient Numeric ID is required" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    if (targetNumericId === callerProfile.numeric_id) {
       return new Response(
        JSON.stringify({ success: false, error: "INVALID_TARGET", message: "You cannot award coins to yourself" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 5. Execute Atomic Database RPC using SERVICE_ROLE
    // Note: award_coins is SECURITY DEFINER but execute permission is restricted to service_role.
    const { data: rpcResult, error: rpcError } = await supabaseAdmin.rpc("award_coins", {
      p_sender_id: callerId,
      p_sender_numeric_id: callerProfile.numeric_id,
      p_is_admin: isAdmin,
      p_is_coinseller: isCoinSeller,
      p_target_numeric_id: targetNumericId,
      p_amount: amount,
      p_reason: reason
    });

    if (rpcError) {
      console.error("RPC Error:", rpcError);
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: "COIN_AWARD_FAILED", 
          message: rpcError.message || "Failed to process coin award" 
        }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Handle application-level errors returned by RPC
    if (rpcResult && rpcResult.success === false) {
       return new Response(
        JSON.stringify(rpcResult),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 6. Return Authoritative Result
    return new Response(
      JSON.stringify({
        success: true,
        target_user_id: rpcResult.target_user_id || "",
        target_coins: rpcResult.target_coins,
        seller_coins: rpcResult.seller_coins,
        message: rpcResult.message
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );

  } catch (err: any) {
    console.error("Function Error:", err);
    return new Response(
      JSON.stringify({ success: false, error: "INTERNAL_ERROR", message: err.message }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
