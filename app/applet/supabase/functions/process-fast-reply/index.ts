// ============================================================================
// Supabase Edge Function: process-fast-reply
// Architecture: Frontend -> Edge Function -> PostgreSQL RPC (process_fast_reply_reward)
// Deploy with: supabase functions deploy process-fast-reply --no-verify-jwt
// ============================================================================

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

// In-memory rate limiting per user (max 15 requests per 10 seconds)
const rateLimitMap = new Map<string, { count: number; resetAt: number }>();

function isRateLimited(userId: string): boolean {
  const now = Date.now();
  const entry = rateLimitMap.get(userId);
  if (!entry || now > entry.resetAt) {
    rateLimitMap.set(userId, { count: 1, resetAt: now + 10_000 });
    return false;
  }
  entry.count++;
  return entry.count > 15;
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

    const responderId = user.id;

    // 2. Anti-abuse rate limiting
    if (isRateLimited(responderId)) {
      return new Response(
        JSON.stringify({ success: false, error: "Too many reward requests. Please slow down." }),
        { status: 429, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 3. Parse and validate ONLY required client parameters
    const body = await req.json().catch(() => ({}));
    const originalMessageId = Number(body.original_message_id);
    const replyMessageId = Number(body.reply_message_id);
    const idempotencyKey = (body.idempotency_key || "").trim();

    if (!originalMessageId || isNaN(originalMessageId)) {
      return new Response(
        JSON.stringify({ success: false, error: "Valid original_message_id is required." }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    if (!replyMessageId || isNaN(replyMessageId)) {
      return new Response(
        JSON.stringify({ success: false, error: "Valid reply_message_id is required." }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    if (!idempotencyKey) {
      return new Response(
        JSON.stringify({ success: false, error: "idempotency_key is required." }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // SECURITY: Reject/ignore any client-sent fields:
    // - response_time / response_seconds (computed purely by PostgreSQL)
    // - reward / diamonds / amount (computed purely by PostgreSQL)
    // - gender / responder_gender / sender_gender (verified purely in PostgreSQL)

    // 4. Call authoritative PostgreSQL RPC
    const { data: rpcResult, error: rpcError } = await supabaseAdmin.rpc("process_fast_reply_reward", {
      p_responder_id: responderId,
      p_original_message_id: originalMessageId,
      p_reply_message_id: replyMessageId,
      p_idempotency_key: idempotencyKey,
    });

    if (rpcError) {
      const errorMessage = rpcError.message || "Failed to process fast reply reward.";
      const status = errorMessage.includes("not found") ? 404 :
                     errorMessage.includes("Conversation") || errorMessage.includes("Invalid") ? 400 : 500;

      return new Response(
        JSON.stringify({ success: false, error: errorMessage }),
        { status, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

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
