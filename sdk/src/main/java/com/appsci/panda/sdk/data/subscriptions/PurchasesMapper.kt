package com.appsci.panda.sdk.data.subscriptions

import com.appsci.panda.sdk.data.subscriptions.local.PurchaseEntity
import com.appsci.panda.sdk.data.subscriptions.local.TYPE_PRODUCT
import com.appsci.panda.sdk.data.subscriptions.local.TYPE_SUBSCRIPTION
import com.appsci.panda.sdk.domain.subscriptions.Purchase
import com.appsci.panda.sdk.domain.subscriptions.SkuType

interface PurchasesMapper {

    fun mapToDomain(entity: PurchaseEntity): Purchase

    fun mapFromBillingPurchases(
            purchases: List<com.android.billingclient.api.Purchase>,
            type: Int
    ): List<PurchaseEntity>

}

class PurchasesMapperImpl : PurchasesMapper {

    override fun mapToDomain(entity: PurchaseEntity): Purchase {
        return when (entity.purchaseType) {
            TYPE_SUBSCRIPTION, TYPE_PRODUCT -> mapPurchase(entity)
            else -> throw IllegalArgumentException("Unknown purchase type ${entity.purchaseType}")
        }
    }

    override fun mapFromBillingPurchases(
            purchases: List<com.android.billingclient.api.Purchase>,
            type: Int
    ): List<PurchaseEntity> {
        return purchases.mapNotNull {
            val productId = it.products.firstOrNull() ?: return@mapNotNull null
            val orderId = it.orderId ?: return@mapNotNull null

            PurchaseEntity(
                    productId = productId,
                    orderId = orderId,
                    purchaseToken = it.purchaseToken,
                    purchaseType = type,
                    synced = false
            )
        }
    }

    private fun mapPurchase(entity: PurchaseEntity): Purchase {
        return Purchase(
                id = entity.productId,
                orderId = entity.orderId,
                token = entity.purchaseToken,
                type = when (entity.purchaseType) {
                    TYPE_PRODUCT -> SkuType.INAPP
                    else -> SkuType.SUBSCRIPTION
                }
        )
    }
}
