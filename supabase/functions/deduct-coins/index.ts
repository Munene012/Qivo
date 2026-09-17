// ============================================================================
// Supabase Edge Function: deduct-coins
// Secure Server-Side Coin Deduction Engine for QIVO
//
// Deploy with Supabase CLI:
//   supabase functions deploy deduct-coins --no-verify-jwt
//
// Every response explicitly states the amount of coins deducted / to be deducted:
//   - coins_deducted: number of coins deducted (or 0 if exempt)
//   - amount_to_be_deducted: original intended deduction amount
//   - message: explicit human-readable notification mentioning the exact coin amount
//   - new_balance: authoritative user coin balance
// ============================================================================

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

interface DeductRequest {
  action: "CHAT" | "PHOTO" | "CALL_MINUTE" | "GIFT" | "AVATAR_FRAME" | "PARTY_ROOM" | "MESSAGE_BLAST" | "GENERAL";
  user_id?: string;
  amount?: number;
  recipient_id?: string;
  recipient_name?: string;
  gift_name?: string;
  call_type?: "VOICE" | "VIDEO";
  caller_gender?: string;
  callee_gender?: string;
  frame_id?: string;
  frame_name?: string;
  room_name?: string;
  title?: string;
  description?: string;
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
          message: "Server configuration error: missing credentials"
        }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceRoleKey);

    // 1. Authenticate caller from Authorization Bearer token
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: "Authorization header required.",
          message: "Authentication token missing" 
        }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const token = authHeader.replace("Bearer ", "").trim();
    let callerUserId = "";

    const { data: { user }, error: authError } = await supabaseAdmin.auth.getUser(token);
    if (!authError && user) {
      callerUserId = user.id;
    }

    const payload: DeductRequest = await req.json().catch(() => ({}));
    const targetUserId = (payload.user_id || callerUserId || "").trim();

    if (!targetUserId) {
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: "Missing user_id for coin deduction.",
          message: "Target user ID is required" 
        }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const action = (payload.action || "GENERAL").toUpperCase();

    // ========================================================================
    // ACTION 1: CHAT MESSAGE DEDUCTION
    // ========================================================================
    if (action === "CHAT") {
      const recipientId = (payload.recipient_id || "").trim();
      const intendedAmount = Number(payload.amount ?? 20);

      const { data, error } = await supabaseAdmin.rpc("deduct_chat_coins", {
        p_sender_id: targetUserId,
        p_receiver_id: recipientId,
        p_message_text: payload.description || "Chat message",
        p_coins_cost: intendedAmount
      });

      if (error) {
        return new Response(
          JSON.stringify({ 
            success: false, 
            error: error.message,
            amount_to_be_deducted: intendedAmount,
            message: `Failed to deduct ${intendedAmount} coins: ${error.message}`
          }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const isExempt = data?.exempt === true;
      const coinsDeducted = isExempt ? 0 : Number(data?.coins_deducted ?? data?.deducted ?? intendedAmount);
      const newBalance = Number(data?.new_balance ?? 0);

      const responsePayload = {
        ...data,
        success: data?.success ?? true,
        amount_to_be_deducted: intendedAmount,
        coins_deducted: coinsDeducted,
        new_balance: newBalance,
        message: isExempt 
          ? `0 coins deducted (Free message exemption). Balance: ${newBalance} coins.`
          : `${coinsDeducted} coins deducted for chat message. Remaining balance: ${newBalance} coins.`
      };

      return new Response(
        JSON.stringify(responsePayload),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ========================================================================
    // ACTION 2: PHOTO MESSAGE DEDUCTION
    // ========================================================================
    if (action === "PHOTO") {
      const recipientId = (payload.recipient_id || "").trim();
      const intendedAmount = Number(payload.amount ?? 20);

      const { data, error } = await supabaseAdmin.rpc("deduct_photo_coins", {
        p_sender_id: targetUserId,
        p_receiver_id: recipientId,
        p_photo_url: payload.description || "Photo message",
        p_coins_cost: intendedAmount
      });

      if (error) {
        return new Response(
          JSON.stringify({ 
            success: false, 
            error: error.message,
            amount_to_be_deducted: intendedAmount,
            message: `Failed to deduct ${intendedAmount} coins: ${error.message}`
          }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const isExempt = data?.exempt === true;
      const coinsDeducted = isExempt ? 0 : Number(data?.coins_deducted ?? data?.deducted ?? intendedAmount);
      const newBalance = Number(data?.new_balance ?? 0);

      const responsePayload = {
        ...data,
        success: data?.success ?? true,
        amount_to_be_deducted: intendedAmount,
        coins_deducted: coinsDeducted,
        new_balance: newBalance,
        message: isExempt 
          ? `0 coins deducted (Exempt). Balance: ${newBalance} coins.`
          : `${coinsDeducted} coins deducted for photo message. Remaining balance: ${newBalance} coins.`
      };

      return new Response(
        JSON.stringify(responsePayload),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ========================================================================
    // ACTION 3: CALL MINUTE DEDUCTION (Voice = 80, Video = 160)
    // ========================================================================
    if (action === "CALL_MINUTE") {
      const calleeId = (payload.recipient_id || "").trim();
      const callType = (payload.call_type || "VOICE").toUpperCase();
      const callerGender = payload.caller_gender || "Male";
      const calleeGender = payload.callee_gender || "Female";
      const expectedRate = (callType === "VIDEO") ? 160 : 80;

      const { data, error } = await supabaseAdmin.rpc("deduct_call_minute_coins", {
        p_caller_id: targetUserId,
        p_caller_gender: callerGender,
        p_callee_id: calleeId,
        p_callee_gender: calleeGender,
        p_call_type: callType
      });

      if (error) {
        return new Response(
          JSON.stringify({ 
            success: false, 
            error: error.message,
            amount_to_be_deducted: expectedRate,
            message: `Failed to deduct ${expectedRate} coins for call: ${error.message}`
          }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const isExempt = data?.exempt === true;
      const coinsDeducted = isExempt ? 0 : Number(data?.rate ?? expectedRate);
      const callerBalance = Number(data?.caller_balance ?? 0);

      const responsePayload = {
        ...data,
        success: data?.success ?? true,
        amount_to_be_deducted: expectedRate,
        coins_deducted: coinsDeducted,
        rate: coinsDeducted,
        new_balance: callerBalance,
        message: isExempt 
          ? `0 coins deducted (Call is free). Balance: ${callerBalance} coins.`
          : `${coinsDeducted} coins deducted for 1 minute ${callType.toLowerCase()} call. Remaining balance: ${callerBalance} coins.`
      };

      return new Response(
        JSON.stringify(responsePayload),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ========================================================================
    // ACTION 4: GIFT DEDUCTION
    // ========================================================================
    if (action === "GIFT") {
      const recipientId = (payload.recipient_id || "").trim();
      const giftName = payload.gift_name || "Gift";
      const coinsCost = Number(payload.amount || 0);

      if (coinsCost <= 0) {
        return new Response(
          JSON.stringify({ 
            success: false, 
            error: "Gift coin amount must be greater than 0.",
            amount_to_be_deducted: coinsCost,
            message: "Gift amount must be at least 1 coin" 
          }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const { data, error } = await supabaseAdmin.rpc("deduct_gift_coins", {
        p_sender_id: targetUserId,
        p_gift_name: giftName,
        p_coins: coinsCost,
        p_receiver_id: recipientId,
        p_receiver_name: payload.recipient_name || "User"
      });

      if (error) {
        return new Response(
          JSON.stringify({ 
            success: false, 
            error: error.message,
            amount_to_be_deducted: coinsCost,
            message: `Failed to deduct ${coinsCost} coins for gift ${giftName}: ${error.message}`
          }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const newBalance = Number(data?.new_balance ?? 0);
      const responsePayload = {
        ...data,
        success: data?.success ?? true,
        amount_to_be_deducted: coinsCost,
        coins_deducted: coinsCost,
        new_balance: newBalance,
        message: `${coinsCost} coins deducted for sending ${giftName}. Remaining balance: ${newBalance} coins.`
      };

      return new Response(
        JSON.stringify(responsePayload),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ========================================================================
    // ACTION 5: AVATAR FRAME PURCHASE
    // ========================================================================
    if (action === "AVATAR_FRAME") {
      const frameId = payload.frame_id || "";
      const frameName = payload.frame_name || frameId;
      const frameCost = Number(payload.amount || 0);

      const { data, error } = await supabaseAdmin.rpc("buy_avatar_frame", {
        p_frame_id: frameId
      });

      if (error) {
        return new Response(
          JSON.stringify({ 
            success: false, 
            error: error.message,
            amount_to_be_deducted: frameCost,
            message: `Failed to purchase avatar frame: ${error.message}`
          }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const newBalance = Number(data?.new_balance ?? 0);
      const responsePayload = {
        ...data,
        success: data?.success ?? true,
        amount_to_be_deducted: frameCost,
        coins_deducted: frameCost,
        new_balance: newBalance,
        message: `${frameCost} coins deducted for frame ${frameName}. Remaining balance: ${newBalance} coins.`
      };

      return new Response(
        JSON.stringify(responsePayload),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ========================================================================
    // ACTION 6: PARTY ROOM CREATION FEE (5,000 coins)
    // ========================================================================
    if (action === "PARTY_ROOM") {
      const roomName = payload.room_name || payload.title || "Party Room";
      const roomFee = 5000;

      const { data, error } = await supabaseAdmin.rpc("deduct_party_room_coins", {
        p_user_id: targetUserId,
        p_room_name: roomName,
        p_amount: roomFee
      });

      if (error) {
        return new Response(
          JSON.stringify({ 
            success: false, 
            error: error.message,
            amount_to_be_deducted: roomFee,
            message: `Failed to deduct ${roomFee} coins for room creation: ${error.message}`
          }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const newBalance = Number(data?.new_balance ?? 0);
      const responsePayload = {
        ...data,
        success: data?.success ?? true,
        amount_to_be_deducted: roomFee,
        coins_deducted: roomFee,
        new_balance: newBalance,
        message: `${roomFee} coins deducted for creating party room "${roomName}". Remaining balance: ${newBalance} coins.`
      };

      return new Response(
        JSON.stringify(responsePayload),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ========================================================================
    // ACTION 7: GENERAL DEDUCTION / ADJUSTMENT
    // ========================================================================
    const amountToDeduct = Math.abs(Number(payload.amount || 0));
    if (amountToDeduct <= 0) {
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: "Deduction amount must be greater than 0.",
          amount_to_be_deducted: 0,
          message: "Amount must be greater than 0"
        }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const { data, error } = await supabaseAdmin.rpc("adjust_user_coins", {
      p_user_id: targetUserId,
      p_amount: -amountToDeduct,
      p_type: payload.action || "DEDUCTION",
      p_title: payload.title || "Coin Deduction",
      p_description: payload.description || `Deducted ${amountToDeduct} coins`
    });

    if (error) {
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: error.message,
          amount_to_be_deducted: amountToDeduct,
          message: `Failed to deduct ${amountToDeduct} coins: ${error.message}`
        }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const newBalance = Number(data?.new_balance ?? 0);
    const responsePayload = {
      ...data,
      success: data?.success ?? true,
      amount_to_be_deducted: amountToDeduct,
      coins_deducted: amountToDeduct,
      new_balance: newBalance,
      message: `${amountToDeduct} coins deducted successfully. Remaining balance: ${newBalance} coins.`
    };

    return new Response(
      JSON.stringify(responsePayload),
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
