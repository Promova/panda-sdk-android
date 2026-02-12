package com.appsci.panda.sdk.data.subscriptions

import com.android.billingclient.api.ProductDetails
import com.appsci.panda.sdk.data.device.DeviceDao
import com.appsci.panda.sdk.data.subscriptions.google.BillingValidator
import com.appsci.panda.sdk.data.subscriptions.google.PurchasesGoogleStore
import com.appsci.panda.sdk.data.subscriptions.local.FileStore
import com.appsci.panda.sdk.data.subscriptions.local.PurchasesLocalStore
import com.appsci.panda.sdk.data.subscriptions.rest.PurchasesRestStore
import com.appsci.panda.sdk.data.subscriptions.rest.ScreenData
import com.appsci.panda.sdk.domain.subscriptions.Purchase
import com.appsci.panda.sdk.domain.subscriptions.SubscriptionScreen
import com.appsci.panda.sdk.domain.subscriptions.SubscriptionState
import com.appsci.panda.sdk.domain.subscriptions.SubscriptionsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class SubscriptionsRepositoryImpl(
    private val localStore: PurchasesLocalStore,
    private val googleStore: PurchasesGoogleStore,
    private val restStore: PurchasesRestStore,
    private val mapper: PurchasesMapper,
    private val intentValidator: BillingValidator,
    private val deviceDao: DeviceDao,
    private val fileStore: FileStore,
) : SubscriptionsRepository {

    private val loadedScreens = mutableMapOf<String, ScreenData>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun sync() {
        fetchHistory()
        saveGooglePurchases()
        acknowledge()
        val userId = deviceDao.requireUserId()
            ?: error("User not authorized")
        val notSent = localStore.getNotSentPurchases()
        for (entity in notSent) {
            val purchase = mapper.mapToDomain(entity)
            restStore.sendPurchase(purchase, userId)
            localStore.markSynced(entity.productId)
        }
    }

    override suspend fun validatePurchase(purchase: Purchase): Boolean {
        saveGooglePurchases()
        val userId = deviceDao.requireUserId()
            ?: error("User not authorized")
        val result = restStore.sendPurchase(purchase, userId)
        localStore.markSynced(purchase.id)
        acknowledge()
        return result
    }

    override suspend fun restore(): List<String> {
        fetchHistory()
        saveGooglePurchases()
        val userId = deviceDao.requireUserId()
            ?: error("User not authorized")
        val purchases = googleStore.getPurchases()
        val restoredIds = mutableListOf<String>()
        for (entity in purchases) {
            val purchase = mapper.mapToDomain(entity)
            val active = restStore.sendPurchase(purchase, userId)
            localStore.markSynced(entity.productId)
            if (active) {
                restoredIds.add(entity.productId)
            }
        }
        return restoredIds
    }

    override suspend fun consumeProducts() {
        googleStore.consumeProducts()
        googleStore.fetchHistory()
    }

    override suspend fun prefetchSubscriptionScreen(
        id: String,
    ): SubscriptionScreen {
        val screenData = loadSubscriptionScreen(id)
        return SubscriptionScreen(
            id = screenData.id,
            name = screenData.name,
            screenHtml = screenData.screenHtml
        )
    }

    override suspend fun getSubscriptionScreen(id: String): SubscriptionScreen {
        val cachedScreen = loadedScreens[id]
        val screenData = cachedScreen ?: loadSubscriptionScreen(id)
        return SubscriptionScreen(
            id = screenData.id,
            name = screenData.name,
            screenHtml = screenData.screenHtml
        )
    }

    override fun getCachedScreen(id: String): SubscriptionScreen? {
        return loadedScreens[id]?.let {
            SubscriptionScreen(
                id = it.id,
                name = it.name,
                screenHtml = it.screenHtml
            )
        }
    }

    override suspend fun getCachedOrDefaultScreen(id: String): SubscriptionScreen {
        val cachedScreen = loadedScreens.values.firstOrNull {
            it.id == id
        }?.let {
            SubscriptionScreen(
                id = it.id,
                name = it.name,
                screenHtml = it.screenHtml
            )
        }
        return cachedScreen ?: getFallbackScreen()
    }

    override suspend fun getFallbackScreen(): SubscriptionScreen =
        fileStore.getSubscriptionScreen()

    override suspend fun getProductsDetails(requests: Map<String, List<String>>): List<ProductDetails> =
        googleStore.getProductsDetails(requests)

    override suspend fun getSubscriptionState(): SubscriptionState {
        val userId = deviceDao.requireUserId()
            ?: error("User not authorized")
        return restStore.getSubscriptionState(userId)
    }

    override suspend fun fetchHistory() {
        try {
            googleStore.fetchHistory()
            Timber.d("fetchHistory success")
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun acknowledge() {
        scope.launch {
            try {
                googleStore.acknowledge()
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    private suspend fun saveGooglePurchases() {
        val purchases = try {
            val googlePurchases = googleStore.getPurchases()
            try {
                intentValidator.validateIntent()
                googlePurchases
            } catch (_: Exception) {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
        localStore.savePurchases(purchases)
    }

    private suspend fun loadSubscriptionScreen(id: String): ScreenData {
        Timber.d("loadSubscriptionScreen $id")
        val screenData = restStore.getSubscriptionScreen(id = id)
        loadedScreens[id] = screenData
        return screenData
    }
}
