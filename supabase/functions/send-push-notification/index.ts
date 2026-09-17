// ============================================================================
// Supabase Edge Function: send-push-notification
// High-Reliability FCM Push Dispatcher for QIVO
//
// Strict Constraint:
// Push notifications must ONLY be used for:
// 1. New chat messages (CHAT_MESSAGE)
// 2. Incoming calls (INCOMING_CALL)
//
// Deploy with:
//   supabase functions deploy send-push-notification --no-verify-jwt
// ============================================================================

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

interface PushPayload {
  type: string;
  recipient_user_id: string;
  sender_id: string;
  sender_name?: string;
  sender_avatar?: string;
  message_text?: string;
  title?: string;
  body?: string;
  call_type?: string;
  call_id?: string;
  conversation_id?: string;
}

// Convert PEM or base64 service account to Google OAuth2 Access Token for FCM HTTP v1
async function getGoogleAccessToken(serviceAccount: Record<string, string>): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const claim = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    exp: now + 3600,
    iat: now,
  };

  const header = { alg: "RS256", typ: "JWT" };
  const encodedHeader = btoa(JSON.stringify(header)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  const encodedClaim = btoa(JSON.stringify(claim)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  const unsignedToken = `${encodedHeader}.${encodedClaim}`;

  // Clean and import private key
  const pem = serviceAccount.private_key
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  const binaryKey = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));

  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    binaryKey.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    new TextEncoder().encode(unsignedToken)
  );

  const encodedSignature = btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
  const jwt = `${unsignedToken}.${encodedSignature}`;

  const tokenResp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });

  if (!tokenResp.ok) {
    const errText = await tokenResp.text();
    throw new Error(`Failed to fetch Google OAuth token: ${errText}`);
  }

  const tokenJson = await tokenResp.json();
  return tokenJson.access_token;
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
        JSON.stringify({ success: false, error: "Supabase service credentials missing." }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceRoleKey);

    const rawBody = await req.json().catch(() => ({}));

    // Support both direct client dispatch and Supabase Database Webhook payloads
    let payload: PushPayload;
    if (rawBody.record && rawBody.type === "INSERT" && rawBody.table === "messages") {
      // Automatic webhook from public.messages table
      const record = rawBody.record;
      payload = {
        type: "CHAT_MESSAGE",
        recipient_user_id: record.recipient_id,
        sender_id: record.sender_id,
        sender_name: record.sender_name || "QIVO User",
        sender_avatar: record.sender_avatar || "",
        message_text: record.message_text || "Sent you a message",
      };
    } else {
      payload = {
        type: (rawBody.type || "").toUpperCase(),
        recipient_user_id: rawBody.recipient_user_id || rawBody.receiver_id || "",
        sender_id: rawBody.sender_id || rawBody.caller_id || "",
        sender_name: rawBody.sender_name || rawBody.caller_name || "QIVO User",
        sender_avatar: rawBody.sender_avatar || rawBody.caller_avatar || "",
        message_text: rawBody.message_text || rawBody.body || rawBody.message || "",
        call_type: (rawBody.call_type || "VOICE").toUpperCase(),
        call_id: rawBody.call_id || "",
        conversation_id: rawBody.conversation_id || rawBody.sender_id || "",
      };
    }

    // 1. Strict constraint verification: CHAT_MESSAGE or INCOMING_CALL ONLY
    if (payload.type !== "CHAT_MESSAGE" && payload.type !== "INCOMING_CALL") {
      return new Response(
        JSON.stringify({ success: false, error: `Unsupported push notification type: ${payload.type}. Must be CHAT_MESSAGE or INCOMING_CALL.` }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    if (!payload.recipient_user_id || !payload.sender_id) {
      return new Response(
        JSON.stringify({ success: false, error: "Missing recipient_user_id or sender_id." }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 2. Blocking check: Ensure sender and recipient have not blocked each other
    const { data: blockRows, error: blockErr } = await supabaseAdmin
      .from("user_blocks")
      .select("id")
      .or(`and(blocker_id.eq.${payload.sender_id},blocked_id.eq.${payload.recipient_user_id}),and(blocker_id.eq.${payload.recipient_user_id},blocked_id.eq.${payload.sender_id})`)
      .limit(1);

    if (!blockErr && blockRows && blockRows.length > 0) {
      return new Response(
        JSON.stringify({ success: true, delivered_count: 0, reason: "Blocked relationship" }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 3. Query recipient device tokens from both fcm_device_tokens and fcm_tokens
    const tokens = new Set<string>();

    const { data: deviceTokens } = await supabaseAdmin
      .from("fcm_device_tokens")
      .select("device_token")
      .eq("user_id", payload.recipient_user_id);

    if (deviceTokens) {
      for (const row of deviceTokens) {
        if (row.device_token && typeof row.device_token === "string" && row.device_token.trim()) {
          tokens.add(row.device_token.trim());
        }
      }
    }

    const { data: legacyTokens } = await supabaseAdmin
      .from("fcm_tokens")
      .select("token")
      .eq("user_id", payload.recipient_user_id);

    if (legacyTokens) {
      for (const row of legacyTokens) {
        if (row.token && typeof row.token === "string" && row.token.trim()) {
          tokens.add(row.token.trim());
        }
      }
    }

    const targetTokens = Array.from(tokens);
    if (targetTokens.length === 0) {
      return new Response(
        JSON.stringify({ success: true, delivered_count: 0, message: "No registered FCM device tokens for recipient." }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 4. Determine FCM delivery mechanism (FCM v1 Service Account OR FCM Server Key)
    let fcmServiceAccountRaw = Deno.env.get("FIREBASE_SERVICE_ACCOUNT") || Deno.env.get("FCM_SERVICE_ACCOUNT");
    const fcmServerKey = Deno.env.get("FCM_SERVER_KEY") || Deno.env.get("FIREBASE_SERVER_KEY");

    let deliveredCount = 0;
    const staleTokens: string[] = [];

    // Prepare standard notification text and data
    const title = payload.type === "INCOMING_CALL"
      ? `Incoming ${payload.call_type} Call`
      : (payload.sender_name || "QIVO User");

    const body = payload.type === "INCOMING_CALL"
      ? `${payload.sender_name || "Someone"} is calling you...`
      : (payload.message_text || "Sent you a message");

    const dataPayload: Record<string, string> = {
      type: payload.type,
      recipient_user_id: payload.recipient_user_id,
      sender_id: payload.sender_id,
      sender_name: payload.sender_name || "QIVO User",
      sender_avatar: payload.sender_avatar || "",
      message_text: payload.message_text || "",
      title: title,
      body: body,
      call_type: payload.call_type || "VOICE",
      call_id: payload.call_id || "",
      conversation_id: payload.conversation_id || payload.sender_id,
      timestamp: new Date().toISOString(),
    };

    if (fcmServiceAccountRaw) {
      // Decode Base64 if applicable
      if (!fcmServiceAccountRaw.trim().startsWith("{")) {
        try {
          fcmServiceAccountRaw = atob(fcmServiceAccountRaw);
        } catch (_) {}
      }

      const serviceAccount = JSON.parse(fcmServiceAccountRaw);
      const projectId = serviceAccount.project_id;
      const accessToken = await getGoogleAccessToken(serviceAccount);

      const fcmEndpoint = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;

      for (const token of targetTokens) {
        const messageBody = {
          message: {
            token: token,
            data: dataPayload,
            android: {
              priority: "high",
              notification: {
                title: title,
                body: body,
                channel_id: payload.type === "INCOMING_CALL" ? "qivo_call_notifications" : "qivo_chat_notifications",
                sound: "default",
                click_action: "FLUTTER_NOTIFICATION_CLICK",
              },
            },
          },
        };

        const fcmResp = await fetch(fcmEndpoint, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(messageBody),
        });

        if (fcmResp.ok) {
          deliveredCount++;
        } else {
          const errResp = await fcmResp.text();
          if (errResp.includes("UNREGISTERED") || errResp.includes("NOT_FOUND")) {
            staleTokens.push(token);
          }
        }
      }
    } else if (fcmServerKey) {
      // Legacy FCM HTTP Protocol
      const legacyEndpoint = "https://fcm.googleapis.com/fcm/send";

      for (const token of targetTokens) {
        const legacyBody = {
          to: token,
          priority: "high",
          notification: {
            title: title,
            body: body,
            sound: "default",
            android_channel_id: payload.type === "INCOMING_CALL" ? "qivo_call_notifications" : "qivo_chat_notifications",
          },
          data: dataPayload,
        };

        const fcmResp = await fetch(legacyEndpoint, {
          method: "POST",
          headers: {
            Authorization: `key=${fcmServerKey}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(legacyBody),
        });

        if (fcmResp.ok) {
          const resJson = await fcmResp.json();
          if (resJson.success >= 1) {
            deliveredCount++;
          } else if (resJson.results && resJson.results[0]?.error === "NotRegistered") {
            staleTokens.push(token);
          }
        }
      }
    } else {
      // Neither Service Account nor Server Key configured in Supabase Secrets
      return new Response(
        JSON.stringify({
          success: false,
          error: "FCM credentials not configured. Please set FIREBASE_SERVICE_ACCOUNT or FCM_SERVER_KEY in Supabase secrets.",
          recipient_token_count: targetTokens.length,
        }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Clean up stale/unregistered tokens
    if (staleTokens.length > 0) {
      await supabaseAdmin
        .from("fcm_device_tokens")
        .delete()
        .in("device_token", staleTokens);

      await supabaseAdmin
        .from("fcm_tokens")
        .delete()
        .in("token", staleTokens);
    }

    return new Response(
      JSON.stringify({
        success: true,
        delivered_count: deliveredCount,
        total_tokens: targetTokens.length,
        cleaned_stale_tokens: staleTokens.length,
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : String(err);
    return new Response(
      JSON.stringify({ success: false, error: message }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
