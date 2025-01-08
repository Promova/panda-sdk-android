package com.appsci.panda.sdk.data.subscriptions.rest

import com.appsci.panda.sdk.data.network.PandaApi
import com.appsci.panda.sdk.data.network.ScreenApi
import com.appsci.panda.sdk.domain.subscriptions.Purchase
import com.appsci.panda.sdk.domain.subscriptions.SkuType
import com.appsci.panda.sdk.domain.subscriptions.SubscriptionState
import io.reactivex.Single
import kotlinx.coroutines.rx2.rxSingle

interface PurchasesRestStore {

    fun sendPurchase(purchase: Purchase, userId: String): Single<Boolean>

    fun getSubscriptionState(userId: String): Single<SubscriptionState>

    fun getSubscriptionScreen(id: String): Single<ScreenData>
}

class PurchasesRestStoreImpl(
    private val pandaApi: PandaApi,
    private val screenApi: ScreenApi,
) : PurchasesRestStore {

    override fun sendPurchase(purchase: Purchase, userId: String): Single<Boolean> {
        return when (purchase.type) {
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
        }.map { it.active }
    }

    override fun getSubscriptionState(userId: String): Single<SubscriptionState> =
        pandaApi.getSubscriptionStatus(userId)
            .map { SubscriptionState.map(it) }

    override fun getSubscriptionScreen(
        id: String,
    ): Single<ScreenData> =
        rxSingle {
            val screenData = screenApi.getSubscriptionScreen(id)
            val html = screenApi.getScreenHtml(screenData.htmlUrl)
            ScreenData(
                id = screenData.id,
                name = screenData.name,
                screenHtml = html,
            )
        }


}
