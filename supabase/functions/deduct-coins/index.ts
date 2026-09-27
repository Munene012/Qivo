// ============================================================================
// Supabase Edge Function: deduct-coins
// Secure Server-Side Coin Deduction Engine for QIVO
//
// Deploy with Supabase CLI:
//   supabase functions deploy deduct-coins --no-verify-jwt
//
// Every response explicitly states the amount of coins deducted / to be deducted:
//   - coins_deducted: number of coins deducted (0 for female users, admins, coin sellers, agents)
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
  action: "CHAT" | "PHOTO" | "CALL_MINUTE" | "GIFT" | "AVATAR_FRAME" | "PARTY_ROOM" | "MESSAGE_BLAST" | "GENERAL" | "AWARD_COINS" | "AWARD" | "TRANSFER";
  user_id?: string;
  sender_id?: string;
  sender_numeric_id?: number | string;
  is_admin?: boolean;
  is_coinseller?: boolean;
  target_numeric_id?: number | string;
  target_user_id?: string;
  sender_gender?: string;
  amount?: number;
  recipient_id?: string;
  receiver_id?: string;
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
          message: "Server configuration error: missing credentials"
        }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceRoleKey);

    // 1. Authenticate caller from Authorization Bearer token (if present)
    const authHeader = req.headers.get("Authorization");
    let callerUserId = "";

    if (authHeader) {
      const token = authHeader.replace("Bearer ", "").trim();
      const { data: { user }, error: authError } = await supabaseAdmin.auth.getUser(token);
      if (!authError && user) {
        callerUserId = user.id;
      }
    }

    const payload: DeductRequest = await req.json().catch(() => ({}));
    const targetUserId = (payload.user_id || payload.sender_id || callerUserId || "").trim();

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

    // 2. Fetch sender profile to check authoritative gender and role exemptions
    const { data: senderProfile } = await supabaseAdmin
      .from("profiles")
      .select("coins, gender, is_admin, is_coinseller, is_agent")
      .eq("id", targetUserId)
      .maybeSingle();

    const rawSenderGender = (
      payload.sender_gender ||
      payload.caller_gender ||
      senderProfile?.gender ||
      ""
    ).trim().toLowerCase();

    // Comprehensive check for female users / non-male users (always free/exempt, 0 coins deducted)
    const isFemale =
      rawSenderGender.includes("female") ||
      rawSenderGender.includes("woman") ||
      rawSenderGender.includes("girl") ||
      rawSenderGender.includes("lady") ||
      rawSenderGender === "f" ||
      rawSenderGender === "w" ||
      rawSenderGender === "" ||
      (rawSenderGender !== "male" && rawSenderGender !== "m" && rawSenderGender !== "man" && rawSenderGender !== "boy");

    const isSenderExempt =
      isFemale ||
      senderProfile?.is_admin === true ||
      senderProfile?.is_coinseller === true ||
      senderProfile?.is_agent === true;

    // Check receiver exemptions if recipient_id provided
    const recipientId = (payload.recipient_id || payload.receiver_id || "").trim();
    let isReceiverExempt = false;
    let receiverName = payload.recipient_name || "User";

    if (recipientId) {
      const { data: receiverProfile } = await supabaseAdmin
        .from("profiles")
        .select("name, is_admin, is_coinseller, is_agent")
        .eq("id", recipientId)
        .maybeSingle();

      if (receiverProfile) {
        receiverName = receiverProfile.name || receiverName;
        isReceiverExempt =
          receiverProfile.is_admin === true ||
          receiverProfile.is_coinseller === true ||
          receiverProfile.is_agent === true;
      }
    }

    const currentBalance = Number(senderProfile?.coins ?? 0);
    const action = (payload.action || "GENERAL").toUpperCase();

    // ========================================================================
    // ACTION 1: CHAT MESSAGE DEDUCTION (15 coins for male, 0 for female)
    // ========================================================================
    if (action === "CHAT") {
      const intendedAmount = Number(payload.amount ?? 15);

      // EXEMPTION: Female users, Admins, Coin Sellers, and Agents text 100% for free!
      if (isSenderExempt || isReceiverExempt) {
        return new Response(
          JSON.stringify({ 
            success: true, 
            exempt: true,
            amount_to_be_deducted: intendedAmount,
            coins_deducted: 0,
            deducted: 0,
            new_balance: currentBalance,
            message: `0 coins deducted (Free message exemption for female users, admins, coin sellers, and agents). Balance: ${currentBalance} coins.`
          }),
          { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      // Check balance for non-exempt male user
      if (currentBalance < intendedAmount) {
        return new Response(
          JSON.stringify({ 
            success: false, 
            error: "INSUFFICIENT_COINS",
            amount_to_be_deducted: intendedAmount,
            coins_deducted: 0,
            new_balance: currentBalance,
            message: `Insufficient coins: ${intendedAmount} coins required, but current balance is ${currentBalance} coins.`
          }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      // Execute deduction in database
      const newBal = currentBalance - intendedAmount;
      await supabaseAdmin
        .from("profiles")
        .update({ coins: newBal, updated_at: new Date().toISOString() })
        .eq("id", targetUserId);

      // Record transaction
      await supabaseAdmin.from("coin_transactions").insert({
        user_id: targetUserId,
        amount: -intendedAmount,
        type: "CHAT_DEDUCT",
        title: `Message to ${receiverName}`,
        description: `${intendedAmount} Coins deducted for message to ${receiverName}`,
        created_at: new Date().toISOString()
      });

      return new Response(
        JSON.stringify({
          success: true,
          exempt: false,
          amount_to_be_deducted: intendedAmount,
          coins_deducted: intendedAmount,
          deducted: intendedAmount,
          new_balance: newBal,
          message: `${intendedAmount} coins deducted for chat message. Remaining balance: ${newBal} coins.`
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ========================================================================
    // ACTION 2: PHOTO MESSAGE DEDUCTION (40 coins for all users except admin, coinseller, and agent)
    // ========================================================================
    if (action === "PHOTO") {
      const intendedAmount = Number(payload.amount ?? 40);

      // EXEMPTION: ONLY Admins, Coin Sellers, and Agents send photos for free
      const isPhotoExempt =
        senderProfile?.is_admin === true ||
        senderProfile?.is_coinseller === true ||
        senderProfile?.is_agent === true;

      if (isPhotoExempt) {
        return new Response(
          JSON.stringify({ 
            success: true, 
            exempt: true,
            amount_to_be_deducted: intendedAmount,
            coins_deducted: 0,
            deducted: 0,
            new_balance: currentBalance,
            message: `0 coins deducted (Free photo exemption for admins, coin sellers, and agents). Balance: ${currentBalance} coins.`
          }),
          { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      // Check balance for non-exempt male user
      if (currentBalance < intendedAmount) {
        return new Response(
          JSON.stringify({ 
            success: false, 
            error: "INSUFFICIENT_COINS",
            amount_to_be_deducted: intendedAmount,
            coins_deducted: 0,
            new_balance: currentBalance,
            message: `Insufficient coins: ${intendedAmount} coins required, but current balance is ${currentBalance} coins.`
          }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      // Execute deduction in database
      const newBal = currentBalance - intendedAmount;
      await supabaseAdmin
        .from("profiles")
        .update({ coins: newBal, updated_at: new Date().toISOString() })
        .eq("id", targetUserId);

      // Record transaction
      await supabaseAdmin.from("coin_transactions").insert({
        user_id: targetUserId,
        amount: -intendedAmount,
        type: "PHOTO_DEDUCT",
        title: `Photo to ${receiverName}`,
        description: `${intendedAmount} Coins deducted for photo sent to ${receiverName}`,
        created_at: new Date().toISOString()
      });

      return new Response(
        JSON.stringify({
          success: true,
          exempt: false,
          amount_to_be_deducted: intendedAmount,
          coins_deducted: intendedAmount,
          deducted: intendedAmount,
          new_balance: newBal,
          message: `${intendedAmount} coins deducted for photo. Remaining balance: ${newBal} coins.`
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ========================================================================
    // ACTION 3: CALL MINUTE DEDUCTION (Voice = 80, Video = 160)
    // ========================================================================
    if (action === "CALL_MINUTE") {
      const callType = (payload.call_type || "VOICE").toUpperCase();
      const expectedRate = (callType === "VIDEO") ? 160 : 80;

      // Female users call for free
      if (isSenderExempt || isReceiverExempt) {
        return new Response(
          JSON.stringify({ 
            success: true, 
            exempt: true,
            amount_to_be_deducted: expectedRate,
            coins_deducted: 0,
            rate: 0,
            new_balance: currentBalance,
            message: `0 coins deducted (Free call exemption). Balance: ${currentBalance} coins.`
          }),
          { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const { data, error } = await supabaseAdmin.rpc("deduct_call_minute_coins", {
        p_caller_id: targetUserId,
        p_caller_gender: rawSenderGender || "Male",
        p_callee_id: recipientId,
        p_callee_gender: payload.callee_gender || "Female",
        p_call_type: callType
      });

      if (error) {
        return new Response(
          JSON.stringify({ 
            success: false, 
            error: error.message,
            amount_to_be_deducted: expectedRate,
            message: `Failed to deduct coins for call: ${error.message}`
          }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const isExempt = data?.exempt === true;
      const coinsDeducted = isExempt ? 0 : Number(data?.rate ?? expectedRate);
      const callerBalance = Number(data?.caller_balance ?? 0);

      return new Response(
        JSON.stringify({
          ...data,
          success: data?.success ?? true,
          amount_to_be_deducted: expectedRate,
          coins_deducted: coinsDeducted,
          rate: coinsDeducted,
          new_balance: callerBalance,
          message: isExempt 
            ? `0 coins deducted (Call is free). Balance: ${callerBalance} coins.`
            : `${coinsDeducted} coins deducted for 1 minute ${callType.toLowerCase()} call. Remaining balance: ${callerBalance} coins.`
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ========================================================================
    // ACTION 4: GIFT DEDUCTION
    // ========================================================================
    if (action === "GIFT") {
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
        p_receiver_name: receiverName
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
      return new Response(
        JSON.stringify({
          ...data,
          success: data?.success ?? true,
          amount_to_be_deducted: coinsCost,
          coins_deducted: coinsCost,
          new_balance: newBalance,
          message: `${coinsCost} coins deducted for sending ${giftName}. Remaining balance: ${newBalance} coins.`
        }),
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
      return new Response(
        JSON.stringify({
          ...data,
          success: data?.success ?? true,
          amount_to_be_deducted: frameCost,
          coins_deducted: frameCost,
          new_balance: newBalance,
          message: `${frameCost} coins deducted for frame ${frameName}. Remaining balance: ${newBalance} coins.`
        }),
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
      return new Response(
        JSON.stringify({
          ...data,
          success: data?.success ?? true,
          amount_to_be_deducted: roomFee,
          coins_deducted: roomFee,
          new_balance: newBalance,
          message: `${roomFee} coins deducted for creating party room "${roomName}". Remaining balance: ${newBalance} coins.`
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ========================================================================
    // ACTION 7: AWARD COINS / P2P TRANSFER (Admin unlimited, Coin Seller balance deducted)
    // ========================================================================
    if (action === "AWARD_COINS" || action === "AWARD" || action === "TRANSFER") {
      const awardAmount = Math.floor(Number(payload.amount || 0));
      const targetNumericId = Number(payload.target_numeric_id || 0);
      const targetUserId = (payload.target_user_id || payload.recipient_id || "").trim();
      const senderNumericId = Number(payload.sender_numeric_id || 0);
      const reason = (payload.reason || "").trim();

      if (awardAmount <= 0) {
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

      // Locate target recipient profile
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

      // Non-admin Coin Seller: DEDUCT from sender's balance
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
        if (currentSellerCoins < awardAmount) {
          return new Response(
            JSON.stringify({ 
              success: false, 
              error: "INSUFFICIENT_BALANCE",
              message: `Insufficient balance! You have ${currentSellerCoins.toLocaleString()} coins available.` 
            }),
            { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
          );
        }

        sellerNewCoins = currentSellerCoins - awardAmount;

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

        await supabaseAdmin.from("coin_transactions").insert({
          user_id: senderProfile.id,
          amount: -awardAmount,
          type: "TRANSFER",
          title: "Coins Transferred",
          description: `Transferred ${awardAmount.toLocaleString()} coins to ${targetProfile.name || 'User'} (ID: ${targetProfile.numeric_id || targetNumericId})`,
          created_at: new Date().toISOString()
        });
      } else {
        // Admin: UNLIMITED coins to award
        if (senderProfile?.id) {
          await supabaseAdmin.from("coin_transactions").insert({
            user_id: senderProfile.id,
            amount: 0,
            type: "ADMIN_AWARD",
            title: "Admin Coin Award",
            description: `Admin awarded ${awardAmount.toLocaleString()} coins to ${targetProfile.name || 'User'} (ID: ${targetProfile.numeric_id || targetNumericId})`,
            created_at: new Date().toISOString()
          });
        }
      }

      // Credit target recipient
      const currentTargetCoins = Number(targetProfile.coins ?? 0);
      const newTargetCoins = currentTargetCoins + awardAmount;

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

      await supabaseAdmin.from("coin_transactions").insert({
        user_id: targetProfile.id,
        amount: awardAmount,
        type: isAdmin ? "AWARD" : "TRANSFER",
        title: isAdmin ? "Admin Coin Award" : "P2P Coin Transfer",
        description: reason || (isAdmin ? "Awarded by Administrator" : `Received from Seller ID ${senderProfile?.numeric_id || senderNumericId}`),
        created_at: new Date().toISOString()
      });

      return new Response(
        JSON.stringify({
          success: true,
          message: `Successfully awarded ${awardAmount.toLocaleString()} coins to ${targetProfile.name || 'User'}! New balance: ${newTargetCoins.toLocaleString()} coins.`,
          seller_coins: isAdmin ? Number(senderProfile?.coins ?? 0) : sellerNewCoins,
          target_coins: newTargetCoins
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ========================================================================
    // ACTION 8: GENERAL DEDUCTION / ADJUSTMENT
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
    return new Response(
      JSON.stringify({
        ...data,
        success: data?.success ?? true,
        amount_to_be_deducted: amountToDeduct,
        coins_deducted: amountToDeduct,
        new_balance: newBalance,
        message: `${amountToDeduct} coins deducted successfully. Remaining balance: ${newBalance} coins.`
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
