// ============================================================================
// Supabase Edge Function: send-gift
// Architecture: Frontend -> Edge Function -> PostgreSQL RPC (process_gift)
// Deploy with: supabase functions deploy send-gift --no-verify-jwt
// Supports: Consecutive Gifting + Time-based percentage conversion (exact decimals)
// ============================================================================

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

// In-memory rate limiting per user (allows fast consecutive gifting up to 20 per 10 seconds)
const rateLimitMap = new Map<string, { count: number; resetAt: number }>();

function isRateLimited(userId: string): boolean {
  const now = Date.now();
  const entry = rateLimitMap.get(userId);
  if (!entry || now > entry.resetAt) {
    rateLimitMap.set(userId, { count: 1, resetAt: now + 10_000 });
    return false;
  }
  entry.count++;
  return entry.count > 20;
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response(
      JSON.stringify({ success: false, error: "Method not allowed. Use POST." }),
      { status: 405, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
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

    // 1. Authenticate caller strictly via Bearer token
    const authHeader = req.headers.get("Authorization");
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      return new Response(
        JSON.stringify({ success: false, error: "Authorization Bearer token required." }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const token = authHeader.replace("Bearer ", "").trim();
    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceRoleKey);

    const { data: { user }, error: authError } = await supabaseAdmin.auth.getUser(token);
    if (authError || !user) {
      return new Response(
        JSON.stringify({ success: false, error: "Invalid or expired session." }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const senderId = user.id;

    // 2. Abuse / Rate-limiting protection
    if (isRateLimited(senderId)) {
      return new Response(
        JSON.stringify({ success: false, error: "Too many gift requests. Please slow down." }),
        { status: 429, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 3. Parse and validate parameters
    const body = await req.json().catch(() => ({}));
    const recipientId = (body.recipient_id || "").trim();
    const giftId = (body.gift_id || "").trim();
    const idempotencyKey = (body.idempotency_key || "").trim();
    const originalMessageId = body.original_message_id ? Number(body.original_message_id) : null;

    if (!recipientId) {
      return new Response(
        JSON.stringify({ success: false, error: "recipient_id is required." }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    if (!giftId) {
      return new Response(
        JSON.stringify({ success: false, error: "gift_id is required." }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    if (!idempotencyKey) {
      return new Response(
        JSON.stringify({ success: false, error: "idempotency_key is required." }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 4. Invoke atomic PostgreSQL RPC 'process_gift'
    const { data: rpcResult, error: rpcError } = await supabaseAdmin.rpc("process_gift", {
      p_sender_id: senderId,
      p_recipient_id: recipientId,
      p_gift_id: giftId,
      p_idempotency_key: idempotencyKey,
      p_original_message_id: originalMessageId,
    });

    if (rpcError) {
      const errorMessage = rpcError.message || "Failed to process gift.";
      const status = errorMessage.includes("Insufficient") ? 400 :
                     errorMessage.includes("Self-gifting") ? 400 :
                     errorMessage.includes("not found") ? 404 : 500;

      return new Response(
        JSON.stringify({ success: false, error: errorMessage }),
        { status, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 5. Return authoritative RPC response
    return new Response(
      JSON.stringify(rpcResult),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );

  } catch (err: any) {
    return new Response(
      JSON.stringify({ success: false, error: err.message || "Internal server error." }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
