package com.stacca.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.stacca.app.data.PreferencesManager
import kotlinx.coroutines.*

/**
 * Gestisce gli acquisti in-app tramite Google Play Billing.
 * Supporta one-time purchase per il premium.
 */
class BillingManager(
    private val context: Context,
    private val onPurchaseResult: (Boolean) -> Unit
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"
        const val PREMIUM_PRODUCT_ID = "stacca_premium"
    }

    private val prefs = PreferencesManager(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    private var productDetails: ProductDetails? = null
    private var isConnected = false
    private var connectionRetryCount = 0
    private val maxRetries = 3

    /** Callback per quando i prodotti sono stati caricati */
    var onProductsReady: (() -> Unit)? = null

    /**
     * Connette al Google Play Billing service.
     */
    fun connect() {
        if (billingClient.isReady) {
            isConnected = true
            scope.launch {
                queryProducts()
                checkExistingPurchases()
            }
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                Log.d(TAG, "Billing setup finished: ${result.responseCode} - ${result.debugMessage}")
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    isConnected = true
                    connectionRetryCount = 0
                    scope.launch {
                        queryProducts()
                        checkExistingPurchases()
                    }
                } else {
                    isConnected = false
                    Log.e(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnected = false
                Log.w(TAG, "Billing service disconnected")
                if (connectionRetryCount < maxRetries) {
                    connectionRetryCount++
                    scope.launch {
                        delay(2000L * connectionRetryCount)
                        connect()
                    }
                }
            }
        })
    }

    /**
     * Interroga i prodotti disponibili.
     */
    private suspend fun queryProducts() {
        withContext(Dispatchers.IO) {
            try {
                val inAppParams = QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        listOf(
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(PREMIUM_PRODUCT_ID)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()
                        )
                    )
                    .build()

                val inAppResult = billingClient.queryProductDetails(inAppParams)
                Log.d(TAG, "Query products result: ${inAppResult.billingResult.responseCode}")

                if (inAppResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val list = inAppResult.productDetailsList
                    if (list != null && list.isNotEmpty()) {
                        productDetails = list[0]
                        Log.d(TAG, "Product found: ${productDetails?.name} - ${productDetails?.oneTimePurchaseOfferDetails?.formattedPrice}")
                        withContext(Dispatchers.Main) {
                            onProductsReady?.invoke()
                        }
                    } else {
                        Log.w(TAG, "Product list is empty for ID: $PREMIUM_PRODUCT_ID")
                    }
                } else {
                    Log.e(TAG, "Query failed: ${inAppResult.billingResult.debugMessage}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying products", e)
            }
        }
    }

    /**
     * Verifica se il billing client è pronto e il prodotto è disponibile.
     */
    fun isReady(): Boolean = isConnected && productDetails != null

    /**
     * Avvia il flusso di acquisto per il prodotto premium.
     * Se il prodotto non è ancora caricato, riprova a connettersi.
     */
    fun launchPurchaseFlow(activity: Activity, isSubscription: Boolean = false) {
        if (!isConnected) {
            Log.w(TAG, "Billing not connected, reconnecting...")
            connect()
            onPurchaseResult(false)
            return
        }

        val details = productDetails
        if (details == null) {
            Log.w(TAG, "Product details not available, retrying query...")
            scope.launch {
                queryProducts()
                // Riprova dopo la query
                val retryDetails = productDetails
                if (retryDetails != null) {
                    launchBillingFlow(activity, retryDetails)
                } else {
                    Log.e(TAG, "Product still not available after retry")
                    onPurchaseResult(false)
                }
            }
            return
        }

        launchBillingFlow(activity, details)
    }

    private fun launchBillingFlow(activity: Activity, details: ProductDetails) {
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        )

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        Log.d(TAG, "Launch billing flow result: ${result.responseCode} - ${result.debugMessage}")
    }

    /**
     * Callback quando un acquisto viene completato o aggiornato.
     */
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        Log.d(TAG, "Purchases updated: ${result.responseCode} - ${result.debugMessage}")
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null && purchases.isNotEmpty()) {
                    purchases.forEach { purchase ->
                        handlePurchase(purchase)
                    }
                } else {
                    // Nessun acquisto nella lista
                    onPurchaseResult(false)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User canceled purchase")
                onPurchaseResult(false)
            }
            else -> {
                Log.e(TAG, "Purchase error: ${result.responseCode} - ${result.debugMessage}")
                onPurchaseResult(false)
            }
        }
    }

    /**
     * Gestisce un acquisto: acknowledge + attiva premium.
     */
    private fun handlePurchase(purchase: Purchase) {
        Log.d(TAG, "Handling purchase: state=${purchase.purchaseState}, acknowledged=${purchase.isAcknowledged}")

        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                scope.launch {
                    try {
                        val ackResult = billingClient.acknowledgePurchase(ackParams)
                        Log.d(TAG, "Acknowledge result: ${ackResult.responseCode}")
                        if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            prefs.isPremium = true
                            onPurchaseResult(true)
                        } else {
                            // Anche se l'acknowledge fallisce, l'acquisto è valido
                            // Il Play Store riprovera l'acknowledge automaticamente
                            prefs.isPremium = true
                            onPurchaseResult(true)
                            Log.w(TAG, "Acknowledge failed but purchase is valid: ${ackResult.debugMessage}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error acknowledging purchase", e)
                        // L'acquisto è comunque valido
                        prefs.isPremium = true
                        onPurchaseResult(true)
                    }
                }
            } else {
                prefs.isPremium = true
                onPurchaseResult(true)
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Log.d(TAG, "Purchase is pending")
            // Acquisto in attesa (es. pagamento in contanti)
            // Non attivare il premium ancora
        }
    }

    /**
     * Verifica gli acquisti esistenti al lancio dell'app.
     */
    fun checkExistingPurchases() {
        if (!isConnected) return

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val inAppParams = QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()

                    val inAppResult = billingClient.queryPurchasesAsync(inAppParams)
                    Log.d(TAG, "Check existing purchases: ${inAppResult.billingResult.responseCode}, count=${inAppResult.purchasesList.size}")

                    if (inAppResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        val hasPremium = inAppResult.purchasesList.any { purchase ->
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                                    purchase.products.contains(PREMIUM_PRODUCT_ID)
                        }
                        if (hasPremium) {
                            prefs.isPremium = true
                            // Acknowledge acquisti non ancora confermati
                            inAppResult.purchasesList.forEach { purchase ->
                                if (!purchase.isAcknowledged && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                                    handlePurchase(purchase)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking existing purchases", e)
                }
            }
        }
    }

    /**
     * Ritorna il prezzo formattato del prodotto premium.
     */
    fun getPremiumPrice(): String? {
        return productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
    }

    /**
     * Rilascia le risorse.
     */
    fun destroy() {
        scope.cancel()
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}
