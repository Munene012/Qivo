// ============================================================================
// Supabase Edge Function: pesapal-ipn
// Secure Server-Side Pesapal v3 IPN Webhook Listener & Transaction Verifier
//
// Deploy with Supabase CLI:
//   supabase functions deploy pesapal-ipn --no-verify-jwt
//
// Secrets needed in Supabase Dashboard (Settings -> Edge Functions -> Secrets):
//   PESAPAL_CONSUMER_KEY=your_key
//   PESAPAL_CONSUMER_SECRET=your_secret
//   PESAPAL_ENV=live (or sandbox)
//   SUPABASE_URL=https://<your-project>.supabase.co
//   SUPABASE_SERVICE_ROLE_KEY=eyJh...
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
    const pesapalKey = Deno.env.get("PESAPAL_CONSUMER_KEY") ?? "";
    const pesapalSecret = Deno.env.get("PESAPAL_CONSUMER_SECRET") ?? "";
    const isLive = (Deno.env.get("PESAPAL_ENV") ?? "live").toLowerCase() === "live";

    const pesapalBaseUrl = isLive
      ? "https://pay.pesapal.com/v3"
      : "https://cybqa.pesapal.com/pesapalv3";

    if (!supabaseUrl || !supabaseServiceRoleKey) {
      return new Response(
        JSON.stringify({ error: "Missing Supabase configuration" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceRoleKey);

    // 1. Extract OrderTrackingId and OrderMerchantReference
    let orderTrackingId = "";
    let orderMerchantReference = "";
    let orderNotificationType = "";

    const url = new URL(req.url);
    orderTrackingId = url.searchParams.get("OrderTrackingId") ?? "";
    orderMerchantReference = url.searchParams.get("OrderMerchantReference") ?? "";
    orderNotificationType = url.searchParams.get("OrderNotificationType") ?? "IPNCHANGE";

    if (!orderTrackingId) {
      try {
        const body = await req.json();
        orderTrackingId = body.OrderTrackingId || body.orderTrackingId || "";
        orderMerchantReference = body.OrderMerchantReference || body.orderMerchantReference || "";
        orderNotificationType = body.OrderNotificationType || body.orderNotificationType || "IPNCHANGE";
      } catch (_) {
        // Body was empty or non-JSON query params
      }
    }

    if (!orderTrackingId) {
      return new Response(
        JSON.stringify({ error: "Missing OrderTrackingId parameter" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 2. Fetch authenticated bearer token from Pesapal API v3
    const tokenRes = await fetch(`${pesapalBaseUrl}/api/Auth/RequestToken`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Accept": "application/json",
      },
      body: JSON.stringify({
        consumer_key: pesapalKey,
        consumer_secret: pesapalSecret,
      }),
    });

    if (!tokenRes.ok) {
      const errText = await tokenRes.text();
      console.error("Pesapal Auth Error:", errText);
      return new Response(
        JSON.stringify({ error: "Pesapal authentication failed", details: errText }),
        { status: 502, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const tokenData = await tokenRes.json();
    const bearerToken = tokenData.token;

    // 3. Query authoritative transaction status directly from Pesapal
    const statusRes = await fetch(
      `${pesapalBaseUrl}/api/Transactions/GetTransactionStatus?orderTrackingId=${encodeURIComponent(orderTrackingId)}`,
      {
        method: "GET",
        headers: {
          "Authorization": `Bearer ${bearerToken}`,
          "Accept": "application/json",
        },
      }
    );

    if (!statusRes.ok) {
      const errText = await statusRes.text();
      console.error("Pesapal Status Check Error:", errText);
      return new Response(
        JSON.stringify({ error: "Failed to verify transaction with Pesapal", details: errText }),
        { status: 502, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const txData = await statusRes.json();
    // Pesapal status_code: 1 = COMPLETED, 2 = FAILED, 3 = REVERSED
    const statusCode = Number(txData.status_code);
    const paymentStatus = (txData.payment_status_description || "").toUpperCase();
    const isCompleted = statusCode === 1 || paymentStatus === "COMPLETED";

    // 4. Check local transaction record in database
    const { data: existingTx, error: txFetchError } = await supabaseAdmin
      .from("pesapal_transactions")
      .select("*")
      .or(`order_tracking_id.eq.${orderTrackingId},reference.eq.${orderMerchantReference}`)
      .maybeSingle();

    if (txFetchError && txFetchError.code !== "PGRST116") {
      console.error("Database lookup error:", txFetchError);
    }

    // 5. If already completed, prevent double crediting (Idempotency)
    if (existingTx && (existingTx.status === "COMPLETED" || existingTx.coins_credited === true)) {
      return new Response(
        JSON.stringify({
          orderNotificationType,
          orderTrackingId,
          orderMerchantReference,
          status: 200,
          message: "Transaction already processed",
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 6. If completed on Pesapal, credit coins using atomic RPC
    if (isCompleted) {
      const targetUserId = existingTx?.user_id;
      const coinsToCredit = Number(existingTx?.coins ?? 0);

      if (targetUserId && coinsToCredit > 0) {
        // Execute server RPC
        const { data: rechargeData, error: rechargeError } = await supabaseAdmin.rpc("recharge_coins", {
          p_user_id: targetUserId,
          p_amount: coinsToCredit,
          p_payment_ref: orderTrackingId,
          p_provider: "PESAPAL"
        });

        if (rechargeError) {
          console.error("Recharge RPC error:", rechargeError);
        } else {
          // Update transaction record
          await supabaseAdmin
            .from("pesapal_transactions")
            .update({
              status: "COMPLETED",
              order_tracking_id: orderTrackingId,
              payment_method: txData.payment_method || "PESAPAL",
              coins_credited: true,
              updated_at: new Date().toISOString()
            })
            .or(`order_tracking_id.eq.${orderTrackingId},reference.eq.${orderMerchantReference}`);
        }
      }
    } else if (statusCode === 2 || paymentStatus === "FAILED") {
      await supabaseAdmin
        .from("pesapal_transactions")
        .update({
          status: "FAILED",
          order_tracking_id: orderTrackingId,
          updated_at: new Date().toISOString()
        })
        .or(`order_tracking_id.eq.${orderTrackingId},reference.eq.${orderMerchantReference}`);
    }

    // 7. Acknowledge Pesapal IPN
    return new Response(
      JSON.stringify({
        orderNotificationType,
        orderTrackingId,
        orderMerchantReference,
        status: 200
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );

  } catch (err: any) {
    console.error("IPN handler error:", err);
    return new Response(
      JSON.stringify({ error: err.message || "Internal server error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
