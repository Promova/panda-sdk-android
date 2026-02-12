package com.appsci.panda.sdk.data.subscriptions.rest

import com.appsci.panda.sdk.data.network.PandaApi
import com.appsci.panda.sdk.data.network.ScreenApi
import com.appsci.panda.sdk.domain.subscriptions.Purchase
import com.appsci.panda.sdk.domain.subscriptions.SkuType
import com.appsci.panda.sdk.domain.subscriptions.SubscriptionState
import timber.log.Timber

interface PurchasesRestStore {

    suspend fun sendPurchase(purchase: Purchase, userId: String): Boolean

    suspend fun getSubscriptionState(userId: String): SubscriptionState

    suspend fun getSubscriptionScreen(id: String): ScreenData
}

class PurchasesRestStoreImpl(
    private val pandaApi: PandaApi,
    private val screenApi: ScreenApi,
) : PurchasesRestStore {

    override suspend fun sendPurchase(purchase: Purchase, userId: String): Boolean {
        val response = when (purchase.type) {
            SkuType.SUBSCRIPTION ->
                pandaApi.sendSubscription(
                    SubscriptionRequest(
                        productId = purchase.id,
                        orderId = purchase.orderId,
                        purchaseToken = purchase.token
                    ),
                    userId = userId
                )

            SkuType.INAPP ->
                pandaApi.sendProduct(
                    ProductRequest(
                        productId = purchase.id,
                        orderId = purchase.orderId,
                        purchaseToken = purchase.token,
                    ),
                    userId = userId
                )
        }
        return response.active
    }

    override suspend fun getSubscriptionState(userId: String): SubscriptionState =
        SubscriptionState.map(pandaApi.getSubscriptionStatus(userId))

    override suspend fun getSubscriptionScreen(
        id: String,
    ): ScreenData {
        Timber.d("getSubscriptionScreen: $id")
        val screenData = screenApi.getSubscriptionScreen(id)
        val html = screenApi.getScreenHtml(screenData.htmlUrl)
        return ScreenData(
            id = screenData.id,
            name = screenData.name,
            screenHtml = html,
        )
    }
}
