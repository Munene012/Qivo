package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class PesapalOrderRequest(
    val id: String = "QIVO-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().take(6).uppercase(),
    val currency: String = "KES",
    val amount: Double,
    val description: String,
    val callbackUrl: String = PesapalConfig.DEFAULT_CALLBACK_URL,
    val notificationId: String,
    val email: String,
    val phoneNumber: String,
    val firstName: String = "Customer",
    val lastName: String = "",
    val countryCode: String = "KE"
)

data class PesapalOrderResult(
    val orderTrackingId: String,
    val merchantReference: String,
    val redirectUrl: String,
    val status: String,
    val rawJson: String
)

data class PesapalTransactionStatus(
    val paymentMethod: String,
    val amount: Double,
    val createdDate: String,
    val confirmationCode: String,
    val paymentStatusDescription: String,
    val statusCode: Int, // 1 = Completed, 0 = Invalid, 2 = Failed, 3 = Reversed, -1 = Pending/Unknown
    val merchantReference: String,
    val message: String
) {
    val isCompleted: Boolean
        get() {
            val statusClean = paymentStatusDescription.trim()
            val isExplicitCompleted = statusClean.equals("Completed", ignoreCase = true) ||
                    statusClean.equals("COMPLETED", ignoreCase = true) ||
                    statusClean.equals("Success", ignoreCase = true)

            val isPendingOrFailed = statusClean.equals("Pending", ignoreCase = true) ||
                    statusClean.equals("PENDING", ignoreCase = true) ||
                    statusClean.equals("InProgress", ignoreCase = true) ||
                    statusClean.equals("Failed", ignoreCase = true) ||
                    statusClean.equals("FAILED", ignoreCase = true) ||
                    statusClean.equals("Invalid", ignoreCase = true) ||
                    statusClean.equals("INVALID", ignoreCase = true) ||
                    statusClean.equals("Reversed", ignoreCase = true)

            if (isPendingOrFailed) return false
            if (isExplicitCompleted) return true
            return statusCode == 1
        }

    val normalizedDbStatus: String
        get() = when {
            isCompleted -> "COMPLETED"
            paymentStatusDescription.equals("Cancelled", ignoreCase = true) || paymentStatusDescription.equals("CANCELLED", ignoreCase = true) -> "CANCELLED"
            paymentStatusDescription.equals("Pending", ignoreCase = true) || paymentStatusDescription.equals("PENDING", ignoreCase = true) || paymentStatusDescription.equals("InProgress", ignoreCase = true) -> "PENDING"
            else -> "FAILED"
        }
}

data class PesapalIpnItem(
    val ipnId: String,
    val url: String,
    val createdDate: String,
    val ipnNotificationType: String,
    val status: Int
)

class PesapalPaymentService(private val context: Context? = null) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private var cachedToken: String? = null
    private var tokenExpiryEpoch: Long = 0L

    /**
     * Request an Auth Token from PesaPal v3
     */
    suspend fun getAuthToken(
        consumerKey: String = PesapalConfig.consumerKey,
        consumerSecret: String = PesapalConfig.consumerSecret,
        forceRefresh: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedToken != null && now < tokenExpiryEpoch - 60_000) {
            return@withContext Result.success(cachedToken!!)
        }

        val key = consumerKey.trim()
        val secret = consumerSecret.trim()

        if (key.isEmpty() || secret.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Pesapal Consumer Key and Secret must not be empty. Please configure them in Pesapal Settings.")
            )
        }

        val baseUrl = PesapalConfig.baseUrl
        val endpoint = "$baseUrl/api/Auth/RequestToken"

        val bodyJson = JSONObject().apply {
            put("consumer_key", key)
            put("consumer_secret", secret)
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (com.example.BuildConfig.DEBUG) {
                    Log.d("PesapalService", "Auth response code: ${response.code}")
                }

                if (response.isSuccessful) {
                    val jsonObj = JSONObject(bodyStr)
                    val token = jsonObj.optString("token", "")
                    if (token.isNotEmpty()) {
                        cachedToken = token
                        // Token valid for roughly 5 minutes by default
                        tokenExpiryEpoch = now + (5 * 60 * 1000)
                        return@withContext Result.success(token)
                    } else {
                        val msg = jsonObj.optString("message", "No token returned from Pesapal")
                        return@withContext Result.failure(Exception(msg))
                    }
                } else {
                    val errMsg = try {
                        val obj = JSONObject(bodyStr)
                        obj.optString("message", obj.optString("error", "HTTP ${response.code}: ${response.message}"))
                    } catch (_: Exception) {
                        "HTTP ${response.code}: $bodyStr"
                    }
                    return@withContext Result.failure(Exception("Pesapal Auth Failed: $errMsg"))
                }
            }
        } catch (e: Exception) {
            Log.e("PesapalService", "Error requesting token", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Register IPN (Instant Payment Notification) URL on PesaPal v3
     */
    suspend fun registerIpn(
        ipnUrl: String = PesapalConfig.ipnUrl,
        notificationType: String = "GET"
    ): Result<String> = withContext(Dispatchers.IO) {
        val tokenResult = getAuthToken()
        if (tokenResult.isFailure) {
            return@withContext Result.failure(tokenResult.exceptionOrNull() ?: Exception("Auth failure"))
        }
        val token = tokenResult.getOrThrow()

        val targetUrl = ipnUrl.trim().ifEmpty { PesapalConfig.DEFAULT_IPN_URL }
        val baseUrl = PesapalConfig.baseUrl
        val endpoint = "$baseUrl/api/URLSetup/RegisterIPN"

        val bodyJson = JSONObject().apply {
            put("url", targetUrl)
            put("ipn_notification_type", notificationType.uppercase())
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (com.example.BuildConfig.DEBUG) {
                    Log.d("PesapalService", "Register IPN response code: ${response.code}")
                }

                if (response.isSuccessful) {
                    val jsonObj = JSONObject(bodyStr)
                    val ipnId = jsonObj.optString("ipn_id", "")
                    if (ipnId.isNotEmpty()) {
                        context?.let { ctx ->
                            PesapalConfig.saveRegisteredIpnId(ctx, ipnId, targetUrl)
                        } ?: run {
                            PesapalConfig.ipnId = ipnId
                            PesapalConfig.ipnUrl = targetUrl
                        }
                        return@withContext Result.success(ipnId)
                    } else {
                        val msg = jsonObj.optString("message", "No IPN ID returned")
                        return@withContext Result.failure(Exception(msg))
                    }
                } else {
                    return@withContext Result.failure(Exception("Failed to register IPN: HTTP ${response.code} $bodyStr"))
                }
            }
        } catch (e: Exception) {
            Log.e("PesapalService", "Error registering IPN", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Get list of already registered IPNs from PesaPal
     */
    suspend fun getRegisteredIpnList(): Result<List<PesapalIpnItem>> = withContext(Dispatchers.IO) {
        val tokenResult = getAuthToken()
        if (tokenResult.isFailure) {
            return@withContext Result.failure(tokenResult.exceptionOrNull() ?: Exception("Auth failure"))
        }
        val token = tokenResult.getOrThrow()

        val baseUrl = PesapalConfig.baseUrl
        val endpoint = "$baseUrl/api/URLSetup/GetIPNList"

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val list = mutableListOf<PesapalIpnItem>()
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            PesapalIpnItem(
                                ipnId = obj.optString("ipn_id", ""),
                                url = obj.optString("url", ""),
                                createdDate = obj.optString("created_date", ""),
                                ipnNotificationType = obj.optString("ipn_notification_type", "GET"),
                                status = obj.optInt("status", 1)
                            )
                        )
                    }
                    return@withContext Result.success(list)
                } else {
                    return@withContext Result.failure(Exception("Failed to fetch IPN list: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    /**
     * Submit an Order to PesaPal directly or via Supabase Edge Function with automatic token fallback
     */
    suspend fun submitOrder(
        order: PesapalOrderRequest,
        packageId: String = "",
        coins: Long = 0L,
        accessToken: String? = null
    ): Result<PesapalOrderResult> = withContext(Dispatchers.IO) {
        val cleanPhone = order.phoneNumber.trim()
        val effectivePhone = if (cleanPhone.isNotBlank()) cleanPhone else "07"

        // 1. Check if direct Pesapal keys are available
        if (PesapalConfig.consumerKey.isNotBlank() && PesapalConfig.consumerSecret.isNotBlank()) {
            val tokenRes = getAuthToken()
            if (tokenRes.isSuccess) {
                val token = tokenRes.getOrThrow()
                val baseUrl = PesapalConfig.baseUrl
                val endpoint = "$baseUrl/api/Transactions/SubmitOrder-Request"

                val directBody = JSONObject().apply {
                    put("id", order.id)
                    put("currency", order.currency)
                    put("amount", order.amount)
                    put("description", order.description)
                    put("callback_url", order.callbackUrl)
                    put("notification_id", order.notificationId.ifBlank { PesapalConfig.ipnId })
                    val billingObj = JSONObject().apply {
                        put("email_address", order.email)
                        put("phone_number", effectivePhone)
                        put("country_code", order.countryCode)
                        put("first_name", order.firstName)
                        put("last_name", order.lastName)
                    }
                    put("billing_address", billingObj)
                }.toString()

                val directReq = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .post(directBody.toRequestBody(jsonMediaType))
                    .build()

                try {
                    client.newCall(directReq).execute().use { directResp ->
                        val dBody = directResp.body?.string() ?: ""
                        if (com.example.BuildConfig.DEBUG) {
                            Log.d("PesapalService", "Direct Pesapal submit response code: ${directResp.code}")
                        }
                        if (directResp.isSuccessful) {
                            val json = JSONObject(dBody)
                            val redirectUrl = json.optString("redirect_url", "")
                            val trackingId = json.optString("order_tracking_id", "")
                            val merchantRef = json.optString("merchant_reference", order.id)
                            if (redirectUrl.isNotBlank()) {
                                return@withContext Result.success(
                                    PesapalOrderResult(
                                        orderTrackingId = trackingId,
                                        merchantReference = merchantRef,
                                        redirectUrl = redirectUrl,
                                        status = "200",
                                        rawJson = dBody
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("PesapalService", "Direct submit failed, trying Edge Function fallback", e)
                }
            }
        }

        // 2. Supabase Edge Function Initiation (Uses Server-stored credentials)
        val authService = SupabaseAuthService()
        var validUserToken = if (!accessToken.isNullOrBlank()) {
            accessToken.trim()
        } else {
            authService.ensureValidToken(context).trim()
        }

        val currentUserId = if (context != null) UserSessionManager.getSession(context)?.userId ?: "" else ""
        val currentUserEmail = if (context != null) UserSessionManager.getSession(context)?.email ?: order.email else order.email
        val apiKey = SupabaseConfig.supabaseAnonKey.trim()

        val supabaseBaseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
        val edgeFunctionUrl = if (supabaseBaseUrl.isNotBlank()) {
            "$supabaseBaseUrl/functions/v1/pesapal?action=initiate"
        } else {
            "https://nfenuymzzvbxebqmtdqz.supabase.co/functions/v1/pesapal?action=initiate"
        }

        val normalizedPackageId = when {
            packageId.isNotBlank() -> packageId
            coins == 10L -> "pkg_10"
            coins == 500L -> "pkg_500"
            coins == 1000L -> "pkg_1000"
            coins == 2000L -> "pkg_2000"
            coins == 5000L -> "pkg_5000"
            coins == 10000L -> "pkg_10000"
            coins == 12500L -> "pkg_12500"
            coins > 0 -> "pkg_${coins}"
            else -> order.id
        }

        fun createPayload(pkgIdVal: String): String {
            return JSONObject().apply {
                put("packageId", pkgIdVal)
                put("package_id", pkgIdVal)
                put("pkgId", pkgIdVal)
                put("pkg_id", pkgIdVal)
                put("package", pkgIdVal)
                put("id", pkgIdVal)
                put("package_name", "${coins} Coins")
                put("name", "${coins} Coins")
                if (currentUserId.isNotBlank()) {
                    put("user_id", currentUserId)
                    put("userId", currentUserId)
                }
                if (coins > 0) {
                    put("coins", coins)
                    put("coin_amount", coins)
                }
                if (order.amount > 0) {
                    put("amount", order.amount)
                    put("price", order.amount)
                }
                if (order.currency.isNotBlank()) {
                    put("currency", order.currency)
                }
                put("phone", effectivePhone)
                put("phoneNumber", effectivePhone)
                put("phone_number", effectivePhone)
                put("email", order.email.ifBlank { currentUserEmail.ifBlank { "customer@qivo.app" } })
                put("email_address", order.email.ifBlank { currentUserEmail.ifBlank { "customer@qivo.app" } })
                if (order.firstName.isNotBlank()) {
                    put("firstName", order.firstName)
                    put("first_name", order.firstName)
                }
                if (order.lastName.isNotBlank()) {
                    put("lastName", order.lastName)
                    put("last_name", order.lastName)
                }
                if (order.countryCode.isNotBlank()) {
                    put("countryCode", order.countryCode)
                    put("country_code", order.countryCode)
                }
                put("callback_url", order.callbackUrl)
                put("notification_id", order.notificationId.ifBlank { PesapalConfig.ipnId })
            }.toString()
        }

        var payload = createPayload(normalizedPackageId)

        fun buildEdgeRequest(userJwt: String, bodyStr: String): Request {
            val reqBuilder = Request.Builder()
                .url(edgeFunctionUrl)
                .addHeader("Content-Type", "application/json")
            if (apiKey.isNotBlank()) {
                reqBuilder.addHeader("apikey", apiKey)
            }
            if (userJwt.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $userJwt")
            }
            return reqBuilder.post(bodyStr.toRequestBody(jsonMediaType)).build()
        }

        try {
            var edgeRequest = buildEdgeRequest(validUserToken, payload)
            var edgeResponse = client.newCall(edgeRequest).execute()
            var edgeBody = edgeResponse.body?.string() ?: ""

            // If 401 occurs because token expired or refreshed on another device, attempt session refresh once
            if (edgeResponse.code == 401 && context != null) {
                edgeResponse.close()
                val refreshed = authService.refreshSession(context)
                if (refreshed is AuthResult.Success && !refreshed.accessToken.isNullOrBlank()) {
                    validUserToken = refreshed.accessToken
                    edgeRequest = buildEdgeRequest(validUserToken, payload)
                    edgeResponse = client.newCall(edgeRequest).execute()
                    edgeBody = edgeResponse.body?.string() ?: ""
                }
            }

            // If edge function returned "invalid or inactive coin package", retry with alternate package ID formats (e.g. numeric ID)
            if (!edgeResponse.isSuccessful && (edgeBody.contains("invalid or inactive", ignoreCase = true) || edgeBody.contains("inactive coin package", ignoreCase = true))) {
                edgeResponse.close()
                val alternatePkgId = if (normalizedPackageId.startsWith("pkg_")) normalizedPackageId.removePrefix("pkg_") else "pkg_$normalizedPackageId"
                payload = createPayload(alternatePkgId)
                edgeRequest = buildEdgeRequest(validUserToken, payload)
                edgeResponse = client.newCall(edgeRequest).execute()
                edgeBody = edgeResponse.body?.string() ?: ""
            }

            edgeResponse.use { resp ->
                if (com.example.BuildConfig.DEBUG) {
                    Log.d("PesapalService", "Edge Function response code: ${resp.code}")
                }

                if (resp.isSuccessful) {
                    val jsonObj = JSONObject(edgeBody)
                    val checkoutUrl = jsonObj.optString("checkoutUrl", jsonObj.optString("redirect_url", ""))
                    val orderTrackingId = jsonObj.optString("orderTrackingId", jsonObj.optString("order_tracking_id", ""))
                    val merchantRef = jsonObj.optString("merchantReference", jsonObj.optString("merchant_reference", order.id))

                    if (checkoutUrl.isNotEmpty()) {
                        return@withContext Result.success(
                            PesapalOrderResult(
                                orderTrackingId = orderTrackingId,
                                merchantReference = merchantRef,
                                redirectUrl = checkoutUrl,
                                status = "200",
                                rawJson = edgeBody
                            )
                        )
                    } else {
                        val errMsg = jsonObj.optString("error", "No checkout URL returned")
                        return@withContext Result.failure(Exception(errMsg))
                    }
                } else {
                    val errMsg = try {
                        val obj = JSONObject(edgeBody)
                        obj.optString("error", obj.optString("message", "Payment initiation error (HTTP ${resp.code})"))
                    } catch (_: Exception) {
                        "Payment initiation error (HTTP ${resp.code})"
                    }
                    return@withContext Result.failure(Exception(errMsg))
                }
            }
        } catch (e: Exception) {
            Log.e("PesapalService", "Error during checkout initiation", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Query transaction status from PesaPal v3 using orderTrackingId
     */
    suspend fun getTransactionStatus(orderTrackingId: String, accessToken: String? = null): Result<PesapalTransactionStatus> = withContext(Dispatchers.IO) {
        if (orderTrackingId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("orderTrackingId is blank"))
        }

        // Try direct Pesapal if credentials exist
        if (PesapalConfig.consumerKey.isNotBlank() && PesapalConfig.consumerSecret.isNotBlank()) {
            val tokenResult = getAuthToken()
            if (tokenResult.isSuccess) {
                val token = tokenResult.getOrThrow()
                val baseUrl = PesapalConfig.baseUrl
                val endpoint = "$baseUrl/api/Transactions/GetTransactionStatus?orderTrackingId=$orderTrackingId"

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()

                try {
                    client.newCall(request).execute().use { response ->
                        val bodyStr = response.body?.string() ?: ""
                        if (com.example.BuildConfig.DEBUG) {
                            Log.d("PesapalService", "Get Status response code: ${response.code}")
                        }

                        if (response.isSuccessful) {
                            val jsonObj = JSONObject(bodyStr)
                            val status = PesapalTransactionStatus(
                                paymentMethod = jsonObj.optString("payment_method", ""),
                                amount = jsonObj.optDouble("amount", 0.0),
                                createdDate = jsonObj.optString("created_date", ""),
                                confirmationCode = jsonObj.optString("confirmation_code", ""),
                                paymentStatusDescription = jsonObj.optString("payment_status_description", ""),
                                statusCode = jsonObj.optInt("status_code", -1),
                                merchantReference = jsonObj.optString("merchant_reference", ""),
                                message = jsonObj.optString("message", "")
                            )
                            return@withContext Result.success(status)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("PesapalService", "Direct Pesapal status check failed, trying Edge Function fallback: ${e.message}")
                }
            }
        }

        // Edge Function status endpoint
        val supabaseBaseUrl = SupabaseConfig.supabaseUrl.trim().removeSuffix("/")
        val apiKey = SupabaseConfig.supabaseAnonKey.trim()
        val baseEdgeUrl = if (supabaseBaseUrl.isNotBlank()) {
            "$supabaseBaseUrl/functions/v1/pesapal"
        } else {
            "https://nfenuymzzvbxebqmtdqz.supabase.co/functions/v1/pesapal"
        }

        val authService = SupabaseAuthService()
        var validUserToken = if (!accessToken.isNullOrBlank()) {
            accessToken.trim()
        } else {
            authService.ensureValidToken(context).trim()
        }

        // Try candidate request strategies: 
        // 1. GET with ?action=status&orderTrackingId=...
        // 2. GET with ?action=status&OrderTrackingId=...
        // 3. POST with { action: "status", orderTrackingId: "..." }
        val candidateRequests = listOf(
            Request.Builder()
                .url("$baseEdgeUrl?action=status&orderTrackingId=$orderTrackingId")
                .addHeader("Accept", "application/json")
                .apply {
                    if (apiKey.isNotBlank()) addHeader("apikey", apiKey)
                    if (validUserToken.isNotBlank()) addHeader("Authorization", "Bearer $validUserToken")
                }
                .get()
                .build(),
            Request.Builder()
                .url("$baseEdgeUrl?action=status&OrderTrackingId=$orderTrackingId")
                .addHeader("Accept", "application/json")
                .apply {
                    if (apiKey.isNotBlank()) addHeader("apikey", apiKey)
                    if (validUserToken.isNotBlank()) addHeader("Authorization", "Bearer $validUserToken")
                }
                .get()
                .build(),
            Request.Builder()
                .url("$baseEdgeUrl?action=status")
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .apply {
                    if (apiKey.isNotBlank()) addHeader("apikey", apiKey)
                    if (validUserToken.isNotBlank()) addHeader("Authorization", "Bearer $validUserToken")
                }
                .post(
                    JSONObject().apply {
                        put("action", "status")
                        put("orderTrackingId", orderTrackingId)
                        put("OrderTrackingId", orderTrackingId)
                        put("order_tracking_id", orderTrackingId)
                    }.toString().toRequestBody(jsonMediaType)
                )
                .build()
        )

        var lastErrorMsg = "Failed to query status"
        for (req in candidateRequests) {
            try {
                var resp = client.newCall(req).execute()
                var bodyStr = resp.body?.string() ?: ""

                // Handle 401 token expiry by refreshing session and retrying once
                if (resp.code == 401 && context != null) {
                    resp.close()
                    val refreshed = authService.refreshSession(context)
                    if (refreshed is AuthResult.Success && !refreshed.accessToken.isNullOrBlank()) {
                        validUserToken = refreshed.accessToken
                        val retryReq = req.newBuilder()
                            .header("Authorization", "Bearer $validUserToken")
                            .build()
                        resp = client.newCall(retryReq).execute()
                        bodyStr = resp.body?.string() ?: ""
                    }
                }

                if (resp.isSuccessful && bodyStr.isNotBlank()) {
                    resp.close()
                    val jsonObj = JSONObject(bodyStr)
                    val targetObj = if (jsonObj.has("data") && jsonObj.get("data") is JSONObject) {
                        jsonObj.getJSONObject("data")
                    } else {
                        jsonObj
                    }

                    val statusDesc = targetObj.optString("payment_status_description",
                        targetObj.optString("paymentStatusDescription",
                            targetObj.optString("status", "")))

                    val statusCodeVal = targetObj.optInt("status_code",
                        targetObj.optInt("statusCode",
                            if (statusDesc.equals("Completed", ignoreCase = true) || statusDesc.equals("COMPLETED", ignoreCase = true)) 1 else -1))

                    val status = PesapalTransactionStatus(
                        paymentMethod = targetObj.optString("payment_method", targetObj.optString("paymentMethod", "")),
                        amount = targetObj.optDouble("amount", 0.0),
                        createdDate = targetObj.optString("created_date", targetObj.optString("createdDate", "")),
                        confirmationCode = targetObj.optString("confirmation_code", targetObj.optString("confirmationCode", "")),
                        paymentStatusDescription = statusDesc,
                        statusCode = statusCodeVal,
                        merchantReference = targetObj.optString("merchant_reference", targetObj.optString("merchantReference", "")),
                        message = targetObj.optString("message", jsonObj.optString("message", ""))
                    )
                    return@withContext Result.success(status)
                } else {
                    lastErrorMsg = try {
                        val obj = JSONObject(bodyStr)
                        obj.optString("error", obj.optString("message", "HTTP ${resp.code}: $bodyStr"))
                    } catch (_: Exception) {
                        "HTTP ${resp.code}: $bodyStr"
                    }
                    resp.close()
                }
            } catch (e: Exception) {
                lastErrorMsg = e.localizedMessage ?: "Network error"
            }
        }

        return@withContext Result.failure(Exception("Failed to check status: $lastErrorMsg"))
    }
}
