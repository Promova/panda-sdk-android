package com.appsci.panda.sdk.data.subscriptions.google

import com.android.billingclient.api.*
import com.appsci.billingktx.client.BillingKtx
import com.appsci.panda.sdk.data.subscriptions.PurchasesMapper
import com.appsci.panda.sdk.data.subscriptions.local.PurchaseEntity
import com.appsci.panda.sdk.data.subscriptions.local.TYPE_PRODUCT
import com.appsci.panda.sdk.data.subscriptions.local.TYPE_SUBSCRIPTION
import kotlinx.coroutines.*
import timber.log.Timber

interface PurchasesGoogleStore {

    suspend fun getPurchases(): List<PurchaseEntity>

    suspend fun consumeProducts()

    suspend fun fetchHistory()

    suspend fun acknowledge()

    suspend fun getProductsDetails(requests: Map<String, List<String>>): List<ProductDetails>

}

class PurchasesGoogleStoreImpl(
        private val billingKtx: BillingKtx,
        private val mapper: PurchasesMapper,
) : PurchasesGoogleStore {

    override suspend fun getPurchases(): List<PurchaseEntity> {
        val subs = billingKtx.getPurchases(BillingClient.ProductType.SUBS)
        val subscriptionEntities = mapper.mapFromBillingPurchases(subs, TYPE_SUBSCRIPTION)

        val inapp = billingKtx.getPurchases(BillingClient.ProductType.INAPP)
        val productEntities = mapper.mapFromBillingPurchases(inapp, TYPE_PRODUCT)

        val result = subscriptionEntities + productEntities
        Timber.d("getPurchases $result")
        return result
    }

    override suspend fun consumeProducts() {
        val purchases = billingKtx.getPurchases(BillingClient.ProductType.INAPP)
        purchases.forEach { purchase ->
            billingKtx.consumeProduct(
                ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            )
        }
    }

    override suspend fun fetchHistory() {
        billingKtx.getPurchases(BillingClient.ProductType.SUBS)
        billingKtx.getPurchases(BillingClient.ProductType.INAPP)
    }

    override suspend fun acknowledge() {
        val subs = billingKtx.getPurchases(BillingClient.ProductType.SUBS)
        val inapp = billingKtx.getPurchases(BillingClient.ProductType.INAPP)

        (subs + inapp)
            .filter { !it.isAcknowledged }
            .forEach { purchase ->
                billingKtx.acknowledge(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                )
            }
    }

    override suspend fun getProductsDetails(requests: Map<String, List<String>>): List<ProductDetails> =
        withContext(Dispatchers.IO) {
            val params: List<QueryProductDetailsParams> = requests.map { (type, ids) ->
                QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        ids.map { id ->
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(id)
                                .setProductType(type)
                                .build()
                        }
                    )
                    .build()
            }

            params.map { async { billingKtx.getProductDetails(it) } }
                .awaitAll()
                .flatten()
        }
}
