package com.example.data

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import org.json.JSONObject
import java.lang.reflect.Method

/**
 * Unified Analytics Service managing Firebase Analytics and ThinkingData Analytics.
 * Provides unified event logging for all key user journeys and monetization funnels.
 */
object AppAnalyticsService {

    private const val TAG = "AppAnalytics"

    // Placeholder constants for ThinkingData Analytics integration
    const val THINKINGDATA_APP_ID = "YOUR_THINKINGDATA_APP_ID"
    const val THINKINGDATA_SERVER_URL = "https://receiver.ta.thinkingdata.cn"

    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var thinkingDataInstance: Any? = null
    private var tdTrackMethod: Method? = null
    private var tdLoginMethod: Method? = null
    private var tdLogoutMethod: Method? = null
    private var isInitialized = false

    /**
     * Initializes Firebase Analytics and ThinkingData SDK.
     */
    fun init(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext

        // 1. Initialize Firebase Analytics
        try {
            firebaseAnalytics = FirebaseAnalytics.getInstance(appContext)
            Log.d(TAG, "Firebase Analytics initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Analytics: ${e.message}")
        }

        // 2. Initialize ThinkingData Analytics SDK (via dynamic reflection wrapper)
        try {
            val appId = if (THINKINGDATA_APP_ID.isNotBlank() && !THINKINGDATA_APP_ID.startsWith("YOUR_")) {
                THINKINGDATA_APP_ID
            } else {
                "qivo_app_analytics_dev"
            }
            
            // Try loading TDConfig / ThinkingAnalyticsSDK
            val configClass = Class.forName("cn.thinkingdata.android.TDConfig")
            val sdkClass = Class.forName("cn.thinkingdata.android.ThinkingAnalyticsSDK")
            
            val getInstanceMethod = configClass.getMethod("getInstance", Context::class.java, String::class.java, String::class.java)
            val configInstance = getInstanceMethod.invoke(null, appContext, appId, THINKINGDATA_SERVER_URL)
            
            val sharedInstanceMethod = sdkClass.getMethod("sharedInstance", configClass)
            thinkingDataInstance = sharedInstanceMethod.invoke(null, configInstance)
            
            tdTrackMethod = sdkClass.getMethod("track", String::class.java, JSONObject::class.java)
            tdLoginMethod = sdkClass.getMethod("login", String::class.java)
            tdLogoutMethod = sdkClass.getMethod("logout")

            Log.d(TAG, "ThinkingData SDK dynamically initialized with App ID: $appId")
        } catch (e: Exception) {
            Log.d(TAG, "ThinkingData SDK note (Ready for configuration): ${e.message}")
        }

        isInitialized = true
    }

    /**
     * Sets the user identifier across both analytics platforms.
     */
    fun setUserId(userId: String) {
        try {
            firebaseAnalytics?.setUserId(userId.ifEmpty { null })
            if (userId.isNotBlank()) {
                tdLoginMethod?.invoke(thinkingDataInstance, userId)
            } else {
                tdLogoutMethod?.invoke(thinkingDataInstance)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting user id: ${e.message}")
        }
    }

    /**
     * Event: sign_up
     */
    fun logSignUp(userId: String, method: String) {
        setUserId(userId)

        // Firebase Analytics
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, method)
            putString("user_id", userId)
        }
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SIGN_UP, bundle)

        // ThinkingData Analytics
        val json = JSONObject().apply {
            put("user_id", userId)
            put("signup_method", method)
        }
        trackThinkingData("sign_up", json)
        Log.d(TAG, "Logged sign_up event: method=$method, userId=$userId")
    }

    /**
     * Event: login
     */
    fun logLogin(userId: String, method: String) {
        setUserId(userId)

        // Firebase Analytics
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, method)
            putString("user_id", userId)
        }
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.LOGIN, bundle)

        // ThinkingData Analytics
        val json = JSONObject().apply {
            put("user_id", userId)
            put("login_method", method)
        }
        trackThinkingData("login", json)
        Log.d(TAG, "Logged login event: method=$method, userId=$userId")
    }

    /**
     * Event: create_party_room (Voice party lounge created)
     */
    fun logPartyRoomCreated(
        roomId: String,
        title: String,
        category: String,
        feeCoins: Double = 5000.0,
        extraProps: Map<String, Any> = emptyMap()
    ) {
        // Firebase Analytics
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.ITEM_ID, roomId)
            putString(FirebaseAnalytics.Param.ITEM_NAME, title)
            putString(FirebaseAnalytics.Param.ITEM_CATEGORY, category)
            putDouble(FirebaseAnalytics.Param.PRICE, feeCoins)
            extraProps.forEach { (k, v) ->
                when (v) {
                    is String -> putString(k, v)
                    is Int -> putInt(k, v)
                    is Long -> putLong(k, v)
                    is Double -> putDouble(k, v)
                    is Boolean -> putBoolean(k, v)
                    else -> putString(k, v.toString())
                }
            }
        }
        firebaseAnalytics?.logEvent("party_room_created", bundle)

        // ThinkingData Analytics
        val json = JSONObject().apply {
            put("room_id", roomId)
            put("title", title)
            put("category", category)
            put("price_coins", feeCoins)
            extraProps.forEach { (k, v) -> put(k, v) }
        }
        trackThinkingData("party_room_created", json)
        Log.d(TAG, "Logged party_room_created event: $roomId - $title")
    }

    /**
     * Event: join_room (Joining a live voice lounge)
     */
    fun logJoinRoom(
        roomId: String,
        title: String,
        category: String
    ) {
        // Firebase Analytics
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.ITEM_ID, roomId)
            putString(FirebaseAnalytics.Param.ITEM_NAME, title)
            putString(FirebaseAnalytics.Param.ITEM_CATEGORY, category)
        }
        firebaseAnalytics?.logEvent("join_room", bundle)

        // ThinkingData Analytics
        val json = JSONObject().apply {
            put("room_id", roomId)
            put("title", title)
            put("category", category)
        }
        trackThinkingData("join_room", json)
    }

    /**
     * Event: search
     */
    fun logSearch(query: String, category: String = "all", filterCount: Int = 0) {
        // Firebase Analytics
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SEARCH_TERM, query)
            putString("search_category", category)
            putInt("filter_count", filterCount)
        }
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SEARCH, bundle)

        // ThinkingData Analytics
        val json = JSONObject().apply {
            put("query", query)
            put("category", category)
            put("filter_count", filterCount)
        }
        trackThinkingData("search", json)
        Log.d(TAG, "Logged search event: query=$query")
    }

    /**
     * Event: payment_started
     */
    fun logPaymentStarted(
        orderId: String,
        amount: Double,
        currency: String,
        method: String,
        itemId: String,
        coinsAmount: Long = 0L
    ) {
        // Firebase Analytics
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.TRANSACTION_ID, orderId)
            putDouble(FirebaseAnalytics.Param.VALUE, amount)
            putString(FirebaseAnalytics.Param.CURRENCY, currency)
            putString(FirebaseAnalytics.Param.PAYMENT_TYPE, method)
            putString(FirebaseAnalytics.Param.ITEM_ID, itemId)
            putLong("coins_amount", coinsAmount)
        }
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.BEGIN_CHECKOUT, bundle)

        // ThinkingData Analytics
        val json = JSONObject().apply {
            put("order_id", orderId)
            put("amount", amount)
            put("currency", currency)
            put("payment_method", method)
            put("item_id", itemId)
            put("coins_amount", coinsAmount)
        }
        trackThinkingData("payment_started", json)
        Log.d(TAG, "Logged payment_started: orderId=$orderId, amount=$amount $currency, method=$method")
    }

    /**
     * Event: payment_success
     */
    fun logPaymentSuccess(
        orderId: String,
        amount: Double,
        currency: String,
        method: String,
        itemId: String,
        coinsAmount: Long = 0L
    ) {
        // Firebase Analytics
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.TRANSACTION_ID, orderId)
            putDouble(FirebaseAnalytics.Param.VALUE, amount)
            putString(FirebaseAnalytics.Param.CURRENCY, currency)
            putString(FirebaseAnalytics.Param.PAYMENT_TYPE, method)
            putString(FirebaseAnalytics.Param.ITEM_ID, itemId)
            putLong("coins_amount", coinsAmount)
        }
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.PURCHASE, bundle)

        // ThinkingData Analytics
        val json = JSONObject().apply {
            put("order_id", orderId)
            put("amount", amount)
            put("currency", currency)
            put("payment_method", method)
            put("item_id", itemId)
            put("coins_amount", coinsAmount)
        }
        trackThinkingData("payment_success", json)
        Log.d(TAG, "Logged payment_success: orderId=$orderId, amount=$amount $currency, method=$method")
    }

    /**
     * Event: payment_failed
     */
    fun logPaymentFailed(
        orderId: String,
        errorReason: String,
        method: String,
        amount: Double = 0.0,
        currency: String = "USD"
    ) {
        // Firebase Analytics
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.TRANSACTION_ID, orderId)
            putString("error_reason", errorReason)
            putString(FirebaseAnalytics.Param.PAYMENT_TYPE, method)
            putDouble(FirebaseAnalytics.Param.VALUE, amount)
            putString(FirebaseAnalytics.Param.CURRENCY, currency)
        }
        firebaseAnalytics?.logEvent("payment_failed", bundle)

        // ThinkingData Analytics
        val json = JSONObject().apply {
            put("order_id", orderId)
            put("error_reason", errorReason)
            put("payment_method", method)
            put("amount", amount)
            put("currency", currency)
        }
        trackThinkingData("payment_failed", json)
        Log.w(TAG, "Logged payment_failed: orderId=$orderId, reason=$errorReason, method=$method")
    }

    /**
     * Custom event logger
     */
    fun logCustomEvent(eventName: String, params: Map<String, Any>) {
        val bundle = Bundle()
        val json = JSONObject()

        params.forEach { (key, value) ->
            when (value) {
                is String -> {
                    bundle.putString(key, value)
                    json.put(key, value)
                }
                is Int -> {
                    bundle.putInt(key, value)
                    json.put(key, value)
                }
                is Long -> {
                    bundle.putLong(key, value)
                    json.put(key, value)
                }
                is Double -> {
                    bundle.putDouble(key, value)
                    json.put(key, value)
                }
                is Float -> {
                    bundle.putFloat(key, value)
                    json.put(key, value.toDouble())
                }
                is Boolean -> {
                    bundle.putBoolean(key, value)
                    json.put(key, value)
                }
                else -> {
                    bundle.putString(key, value.toString())
                    json.put(key, value.toString())
                }
            }
        }

        firebaseAnalytics?.logEvent(eventName, bundle)
        trackThinkingData(eventName, json)
    }

    private fun trackThinkingData(eventName: String, json: JSONObject) {
        try {
            tdTrackMethod?.invoke(thinkingDataInstance, eventName, json)
        } catch (e: Exception) {
            Log.e(TAG, "ThinkingData track error for $eventName: ${e.message}")
        }
    }
}
