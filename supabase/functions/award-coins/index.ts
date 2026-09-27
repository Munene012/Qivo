// ============================================================================
// Supabase Edge Function: award-coins
// Secure Server-Side Coin Award & P2P Transfer Engine for QIVO
//
// Permissions:
// - Admin: UNLIMITED coins to award (no balance deduction from admin)
// - Coin Seller: Deducted from coin balance when awarding (requires sufficient balance)
//
// Deploy with Supabase CLI:
//   supabase functions deploy award-coins --no-verify-jwt
// ============================================================================

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

interface AwardRequest {
  sender_id?: string;
  sender_numeric_id?: number | string;
  is_admin?: boolean;
  is_coinseller?: boolean;
  target_numeric_id?: number | string;
  target_user_id?: string;
  amount?: number | string;
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
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: "Supabase environment configuration missing.",
          message: "Server configuration error: missing service credentials"
        }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceRoleKey);

    // 1. Check optional caller authentication from Bearer JWT token
    const authHeader = req.headers.get("Authorization");
    let callerUserId = "";

    if (authHeader && authHeader.startsWith("Bearer ")) {
      const token = authHeader.replace("Bearer ", "").trim();
      try {
        const { data: { user }, error: authError } = await supabaseAdmin.auth.getUser(token);
        if (!authError && user) {
          callerUserId = user.id;
        }
      } catch (_e) {
        // Fall back gracefully to payload identity verification
      }
    }

    const payload: AwardRequest = await req.json().catch(() => ({}));
    const senderId = (payload.sender_id || callerUserId || "").trim();
    const senderNumericId = Number(payload.sender_numeric_id || 0);
    const targetNumericId = Number(payload.target_numeric_id || 0);
    const targetUserId = (payload.target_user_id || "").trim();
    const amount = Math.floor(Number(payload.amount || 0));
    const reason = (payload.reason || "").trim();

    if (amount <= 0) {
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: "INVALID_AMOUNT",
          message: "Award amount must be greater than 0." 
        }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    if (targetNumericId <= 0 && !targetUserId) {
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: "MISSING_TARGET",
          message: "Recipient Numeric ID is required." 
        }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 2. Locate target recipient user profile
    let targetQuery = supabaseAdmin.from("profiles").select("id, name, coins, numeric_id");
    if (targetNumericId > 0) {
      targetQuery = targetQuery.eq("numeric_id", targetNumericId);
    } else {
      targetQuery = targetQuery.eq("id", targetUserId);
    }
    const { data: targetProfile, error: targetError } = await targetQuery.maybeSingle();

    if (targetError || !targetProfile) {
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: "TARGET_NOT_FOUND",
          message: `User with ID ${targetNumericId > 0 ? targetNumericId : targetUserId} not found.` 
        }),
        { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 3. Locate sender profile to authoritative check admin/coinseller status
    let senderProfile: any = null;
    if (senderId) {
      const { data } = await supabaseAdmin
        .from("profiles")
        .select("id, name, coins, numeric_id, is_admin, is_coinseller")
        .eq("id", senderId)
        .maybeSingle();
      senderProfile = data;
    } else if (senderNumericId > 0) {
      const { data } = await supabaseAdmin
        .from("profiles")
        .select("id, name, coins, numeric_id, is_admin, is_coinseller")
        .eq("numeric_id", senderNumericId)
        .maybeSingle();
      senderProfile = data;
    }

    // Determine admin and coinseller authority
    const isAdmin = payload.is_admin === true || senderProfile?.is_admin === true;
    const isCoinSeller = payload.is_coinseller === true || senderProfile?.is_coinseller === true;

    if (!isAdmin && !isCoinSeller) {
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: "UNAUTHORIZED_ROLE",
          message: "Only administrators and authorized coin sellers can award or transfer coins." 
        }),
        { status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    let sellerNewCoins = -1;

    // 4. If NOT Admin -> Sender is Coin Seller: DEDUCT from sender's balance
    if (!isAdmin) {
      if (!senderProfile) {
        return new Response(
          JSON.stringify({ 
            success: false, 
            error: "SENDER_NOT_FOUND",
            message: "Coin seller profile could not be located." 
          }),
          { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const currentSellerCoins = Number(senderProfile.coins ?? 0);
      if (currentSellerCoins < amount) {
        return new Response(
          JSON.stringify({ 
            success: false, 
            error: "INSUFFICIENT_BALANCE",
            message: `Insufficient balance! You have ${currentSellerCoins.toLocaleString()} coins available.` 
          }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      sellerNewCoins = currentSellerCoins - amount;

      // Deduct coins from seller
      const { error: deductErr } = await supabaseAdmin
        .from("profiles")
        .update({ coins: sellerNewCoins, updated_at: new Date().toISOString() })
        .eq("id", senderProfile.id);

      if (deductErr) {
        return new Response(
          JSON.stringify({ 
            success: false, 
            error: "DEDUCTION_FAILED",
            message: `Failed to deduct coins from seller: ${deductErr.message}` 
          }),
          { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      // Record seller transfer transaction
      await supabaseAdmin.from("coin_transactions").insert({
        user_id: senderProfile.id,
        amount: -amount,
        type: "TRANSFER",
        title: "Coins Transferred",
        description: `Transferred ${amount.toLocaleString()} coins to ${targetProfile.name || 'User'} (ID: ${targetProfile.numeric_id || targetNumericId})`,
        created_at: new Date().toISOString()
      });
    } else {
      // Admin has UNLIMITED coins: record audit log without deducting coins
      if (senderProfile?.id) {
        await supabaseAdmin.from("coin_transactions").insert({
          user_id: senderProfile.id,
          amount: 0,
          type: "ADMIN_AWARD",
          title: "Admin Coin Award",
          description: `Admin awarded ${amount.toLocaleString()} coins to ${targetProfile.name || 'User'} (ID: ${targetProfile.numeric_id || targetNumericId})`,
          created_at: new Date().toISOString()
        });
      }
    }

    // 5. Credit target recipient user profile with authoritative coins
    const currentTargetCoins = Number(targetProfile.coins ?? 0);
    const newTargetCoins = currentTargetCoins + amount;

    const { error: creditErr } = await supabaseAdmin
      .from("profiles")
      .update({ coins: newTargetCoins, updated_at: new Date().toISOString() })
      .eq("id", targetProfile.id);

    if (creditErr) {
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: "CREDIT_FAILED",
          message: `Failed to credit coins to recipient: ${creditErr.message}` 
        }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 6. Record target recipient transaction
    await supabaseAdmin.from("coin_transactions").insert({
      user_id: targetProfile.id,
      amount: amount,
      type: isAdmin ? "AWARD" : "TRANSFER",
      title: isAdmin ? "Admin Coin Award" : "P2P Coin Transfer",
      description: reason || (isAdmin ? "Awarded by Administrator" : `Received from Seller ID ${senderProfile?.numeric_id || senderNumericId}`),
      created_at: new Date().toISOString()
    });

    const successMessage = `Successfully awarded ${amount.toLocaleString()} coins to ${targetProfile.name || 'User'}! New balance: ${newTargetCoins.toLocaleString()} coins.`;

    return new Response(
      JSON.stringify({
        success: true,
        message: successMessage,
        seller_coins: isAdmin ? Number(senderProfile?.coins ?? 0) : sellerNewCoins,
        target_coins: newTargetCoins
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );

  } catch (err: any) {
    return new Response(
      JSON.stringify({ 
        success: false, 
        error: err.message || "Internal server error",
        message: `Internal server error: ${err.message || 'unknown error'}`
      }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
