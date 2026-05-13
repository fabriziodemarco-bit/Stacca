package com.stacca.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.stacca.app.data.PreferencesManager
import kotlinx.coroutines.*

/**
 * Gestisce gli acquisti in-app tramite Google Play Billing.
 * Supporta sia subscription che one-time purchase per il premium.
 * Usa le KTX coroutine extensions per evitare problemi con i tipi Java.
 */
class BillingManager(
    private val context: Context,
    private val onPurchaseResult: (Boolean) -> Unit
) : PurchasesUpdatedListener {

    companion object {
        const val PREMIUM_PRODUCT_ID = "stacca_premium"
        const val PREMIUM_SUB_ID = "stacca_premium_monthly"
    }

    private val prefs = PreferencesManager(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()

    private var productDetails: ProductDetails? = null
    private var subDetails: ProductDetails? = null

    /**
     * Connette al Google Play Billing service.
     */
    fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        queryProducts()
                        checkExistingPurchases()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                scope.launch {
                    delay(3000)
                    connect()
                }
            }
        })
    }

    /**
     * Interroga i prodotti disponibili usando le KTX coroutine extensions.
     */
    private suspend fun queryProducts() {
        withContext(Dispatchers.IO) {
            try {
                // Query per in-app purchase (one-time)
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
                if (inAppResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val list = inAppResult.productDetailsList
                    if (list != null && list.isNotEmpty()) {
                        productDetails = list[0]
                    }
                }

                // Query per subscription
                val subParams = QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        listOf(
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(PREMIUM_SUB_ID)
                                .setProductType(BillingClient.ProductType.SUBS)
                                .build()
                        )
                    )
                    .build()

                val subResult = billingClient.queryProductDetails(subParams)
                if (subResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val list = subResult.productDetailsList
                    if (list != null && list.isNotEmpty()) {
                        subDetails = list[0]
                    }
                }
            } catch (e: Exception) {
                // Errore di rete o billing, ignora silenziosamente
            }
        }
    }

    /**
     * Avvia il flusso di acquisto per il prodotto premium.
     */
    fun launchPurchaseFlow(activity: Activity, isSubscription: Boolean = false) {
        val details = if (isSubscription) subDetails else productDetails
        if (details == null) {
            onPurchaseResult(false)
            return
        }

        val productDetailsParamsList = if (isSubscription) {
            val offerDetails = details.subscriptionOfferDetails
            if (offerDetails == null || offerDetails.isEmpty()) {
                onPurchaseResult(false)
                return
            }
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(offerDetails[0].offerToken)
                    .build()
            )
        } else {
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .build()
            )
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    /**
     * Callback quando un acquisto viene completato o aggiornato.
     */
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    handlePurchase(purchase)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                onPurchaseResult(false)
            }
            else -> {
                onPurchaseResult(false)
            }
        }
    }

    /**
     * Gestisce un acquisto: acknowledge + attiva premium.
     */
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                scope.launch {
                    val ackResult = billingClient.acknowledgePurchase(ackParams)
                    if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        prefs.isPremium = true
                        onPurchaseResult(true)
                    }
                }
            } else {
                prefs.isPremium = true
                onPurchaseResult(true)
            }
        }
    }

    /**
     * Verifica gli acquisti esistenti al lancio dell'app.
     */
    fun checkExistingPurchases() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Check in-app purchases
                    val inAppParams = QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()

                    val inAppResult = billingClient.queryPurchasesAsync(inAppParams)
                    if (inAppResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        val hasPremium = inAppResult.purchasesList.any { purchase ->
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                                    purchase.products.contains(PREMIUM_PRODUCT_ID)
                        }
                        if (hasPremium) {
                            prefs.isPremium = true
                            return@withContext
                        }
                    }

                    // Check subscriptions
                    val subParams = QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()

                    val subResult = billingClient.queryPurchasesAsync(subParams)
                    if (subResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        val hasActiveSub = subResult.purchasesList.any { purchase ->
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                                    purchase.products.contains(PREMIUM_SUB_ID)
                        }
                        prefs.isPremium = hasActiveSub || prefs.isPremium
                    }
                } catch (e: Exception) {
                    // Errore di verifica, mantieni lo stato corrente
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
     * Ritorna il prezzo formattato della subscription premium.
     */
    fun getSubscriptionPrice(): String? {
        val offerDetails = subDetails?.subscriptionOfferDetails ?: return null
        if (offerDetails.isEmpty()) return null
        val phases = offerDetails[0].pricingPhases.pricingPhaseList
        return if (phases.isNotEmpty()) phases[0].formattedPrice else null
    }

    /**
     * Rilascia le risorse.
     */
    fun destroy() {
        scope.cancel()
        billingClient.endConnection()
    }
}
