package com.appsci.panda.sdk.domain.subscriptions

import com.android.billingclient.api.ProductDetails

interface SubscriptionsRepository {

    /**
     * returns [SubscriptionStatus] based on purchases from billing and local store
     */
    suspend fun getSubscriptionState(): SubscriptionState

    /**
     * Fetches purchases from billing and sends to rest store
     */
    suspend fun sync()

    suspend fun restore(): List<String>

    suspend fun validatePurchase(purchase: Purchase): Boolean

    /**
     * Consumes all available products and refreshes all purchases
     */
    suspend fun consumeProducts()

    suspend fun fetchHistory()

    suspend fun prefetchSubscriptionScreen(id: String): SubscriptionScreen

    suspend fun getSubscriptionScreen(id: String): SubscriptionScreen

    fun getCachedScreen(id: String): SubscriptionScreen?

    suspend fun getCachedOrDefaultScreen(id: String): SubscriptionScreen

    suspend fun getFallbackScreen(): SubscriptionScreen

    suspend fun getProductsDetails(requests: Map<String, List<String>>): List<ProductDetails>
}
