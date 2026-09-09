package com.example.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Google Play Billing Service integrating Play Billing Library v7.x for consumable coin purchases.
 */
class GooglePlayBillingService(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onCoinsCredited: (Long) -> Unit = {}
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "PlayBillingService"

        // Default Google Play In-App Product IDs matching Qivo Coin packages
        val DEFAULT_PRODUCT_IDS = listOf(
            "qivo_coins_100",
            "qivo_coins_500",
            "qivo_coins_1200",
            "qivo_coins_3000",
            "qivo_coins_6500",
            "qivo_coins_14000"
        )
    }

    private var billingClient: BillingClient? = null
    private val profileService = SupabaseProfileService()

    private val _productsMap = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productsMap: StateFlow<Map<String, ProductDetails>> = _productsMap.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var activeUserId: String = ""
    private var pendingCoinPackage: CoinPackage? = null

    init {
        initializeBillingClient()
    }

    private fun initializeBillingClient() {
        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(pendingPurchasesParams)
            .build()

        startConnection()
    }

    fun startConnection(onConnected: (() -> Unit)? = null) {
        val client = billingClient ?: return
        if (client.isReady) {
            _isReady.value = true
            onConnected?.invoke()
            return
        }

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "BillingClient connected successfully.")
                    _isReady.value = true
                    queryProducts()
                    onConnected?.invoke()
                } else {
                    Log.w(TAG, "Billing setup failed with responseCode: ${billingResult.responseCode} - ${billingResult.debugMessage}")
                    _isReady.value = false
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected. Retrying...")
                _isReady.value = false
            }
        })
    }

    /**
     * Queries Google Play for configured in-app products.
     */
    fun queryProducts(productIds: List<String> = DEFAULT_PRODUCT_IDS) {
        scope.launch(Dispatchers.IO) {
            val client = billingClient
            if (client == null || !client.isReady) {
                Log.w(TAG, "BillingClient not ready during queryProducts")
                return@launch
            }

            val productList = productIds.map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            try {
                val result = client.queryProductDetails(params)
                if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val map = result.productDetailsList?.associateBy { it.productId } ?: emptyMap()
                    _productsMap.value = map
                    Log.d(TAG, "Fetched ${map.size} products from Google Play Billing")
                } else {
                    Log.w(TAG, "queryProductDetails response: ${result.billingResult.responseCode}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying product details: ${e.message}", e)
            }
        }
    }

    /**
     * Launches the Google Play billing flow for a specific CoinPackage.
     */
    fun launchPurchase(
        activity: Activity,
        userId: String,
        coinPackage: CoinPackage,
        customProductId: String? = null,
        onError: (String) -> Unit
    ) {
        activeUserId = userId
        pendingCoinPackage = coinPackage

        val productId = customProductId ?: getProductIdForPackage(coinPackage)
        val productDetails = _productsMap.value[productId]

        // Log payment started event to Firebase & ThinkingData
        AppAnalyticsService.logPaymentStarted(
            orderId = "gplay_${System.currentTimeMillis()}",
            amount = coinPackage.priceUsd,
            currency = "USD",
            method = "google_play_billing",
            itemId = productId,
            coinsAmount = coinPackage.coins
        )

        if (productDetails != null) {
            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build()
            )

            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .setObfuscatedAccountId(userId)
                .build()

            val responseCode = billingClient?.launchBillingFlow(activity, billingFlowParams)?.responseCode
            if (responseCode != BillingClient.BillingResponseCode.OK) {
                val errorMsg = "Could not launch Google Play Billing: response code $responseCode"
                AppAnalyticsService.logPaymentFailed(
                    orderId = "gplay_${System.currentTimeMillis()}",
                    errorReason = errorMsg,
                    method = "google_play_billing",
                    amount = coinPackage.priceUsd,
                    currency = "USD"
                )
                onError(errorMsg)
            }
        } else {
            // If product details not loaded or in mock/test sandbox, we query and report
            Log.w(TAG, "Product details for $productId not found in Google Play Console. Check product ID.")
            // Try reconnecting
            startConnection {
                queryProducts()
            }
            onError("Google Play Product ($productId) is syncing. If test item, please configure in Play Console.")
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled Google Play purchase")
            AppAnalyticsService.logPaymentFailed(
                orderId = "canceled",
                errorReason = "User canceled purchase",
                method = "google_play_billing"
            )
        } else {
            val errorMsg = "Google Play Purchase failed: ${billingResult.responseCode} - ${billingResult.debugMessage}"
            Log.w(TAG, errorMsg)
            AppAnalyticsService.logPaymentFailed(
                orderId = "failed_${System.currentTimeMillis()}",
                errorReason = errorMsg,
                method = "google_play_billing"
            )
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            scope.launch(Dispatchers.IO) {
                // 1. Consume the purchase so user can buy coins again
                val consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                val consumeResult = billingClient?.consumePurchase(consumeParams)
                if (consumeResult?.billingResult?.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Successfully consumed purchase: ${purchase.orderId}")
                }

                // 2. Credit coins to Supabase user profile
                val pkg = pendingCoinPackage
                val coinsToAdd = pkg?.coins ?: 100L
                val targetUserId = activeUserId.ifEmpty { purchase.accountIdentifiers?.obfuscatedAccountId ?: "" }

                if (targetUserId.isNotBlank()) {
                    val newBalance = profileService.topUpCoins(targetUserId, coinsToAdd, "Google Play")
                    if (newBalance > 0) {
                        withContext(Dispatchers.Main) {
                            onCoinsCredited(newBalance)
                        }
                    }
                }

                // 3. Log payment_success in Analytics
                AppAnalyticsService.logPaymentSuccess(
                    orderId = purchase.orderId ?: "gplay_${purchase.purchaseTime}",
                    amount = pkg?.priceUsd ?: 0.99,
                    currency = "USD",
                    method = "google_play_billing",
                    itemId = purchase.products.firstOrNull() ?: "coins",
                    coinsAmount = coinsToAdd
                )
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Log.d(TAG, "Purchase is pending completion.")
        }
    }

    private fun getProductIdForPackage(pkg: CoinPackage): String {
        return when {
            pkg.coins <= 100 -> "qivo_coins_100"
            pkg.coins <= 500 -> "qivo_coins_500"
            pkg.coins <= 1200 -> "qivo_coins_1200"
            pkg.coins <= 3000 -> "qivo_coins_3000"
            pkg.coins <= 6500 -> "qivo_coins_6500"
            else -> "qivo_coins_14000"
        }
    }

    fun endConnection() {
        try {
            billingClient?.endConnection()
        } catch (_: Exception) {}
    }
}
