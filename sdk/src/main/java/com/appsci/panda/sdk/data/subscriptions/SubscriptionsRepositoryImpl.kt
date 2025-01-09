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
import com.appsci.panda.sdk.domain.utils.rx.DefaultCompletableObserver
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single
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

    override fun sync(): Completable {
        return fetchHistory()
            .andThen(saveGooglePurchases())
            .doOnComplete {
                acknowledge()
            }
            .andThen(deviceDao.requireUserId())
            .flatMapCompletable { userId ->
                localStore.getNotSentPurchases()
                    .flatMapPublisher { Flowable.fromIterable(it) }
                    .concatMapCompletable { entity ->
                        val purchase = mapper.mapToDomain(entity)
                        return@concatMapCompletable restStore.sendPurchase(purchase, userId)
                            .doOnSuccess {
                                localStore.markSynced(entity.productId)
                            }.ignoreElement()
                    }
            }
    }

    override fun validatePurchase(purchase: Purchase): Single<Boolean> {

        return saveGooglePurchases()
            .andThen(deviceDao.requireUserId())
            .flatMap {
                restStore.sendPurchase(purchase, it)
                    .doOnSuccess {
                        localStore.markSynced(purchase.id)
                    }
            }.doAfterSuccess {
                acknowledge()
            }
    }

    override fun restore(): Single<List<String>> =
        fetchHistory()
            .andThen(saveGooglePurchases())
            .andThen(deviceDao.requireUserId())
            .flatMap { userId ->
                googleStore.getPurchases()
                    .flatMapPublisher { Flowable.fromIterable(it) }
                    .flatMapMaybe { entity ->
                        val purchase = mapper.mapToDomain(entity)
                        return@flatMapMaybe restStore.sendPurchase(purchase, userId)
                            .doOnSuccess {
                                localStore.markSynced(entity.productId)
                            }.filter { it }
                            .map { entity.productId }
                    }.toList()
            }

    override fun consumeProducts(): Completable =
        googleStore.consumeProducts()
            .andThen(googleStore.fetchHistory())

    override fun prefetchSubscriptionScreen(
        id: String,
    ): Single<SubscriptionScreen> {
        return loadSubscriptionScreen(id)
            .map {
                SubscriptionScreen(
                    id = it.id,
                    name = it.name,
                    screenHtml = it.screenHtml
                )
            }
    }

    override fun getSubscriptionScreen(id: String): Single<SubscriptionScreen> {
        val cachedScreen = loadedScreens[id]
        return (if (cachedScreen != null) {
            Single.just(cachedScreen)
        } else {
            loadSubscriptionScreen(id)
        }).map {
            SubscriptionScreen(
                id = it.id,
                name = it.name,
                screenHtml = it.screenHtml
            )
        }

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

    override fun getCachedOrDefaultScreen(id: String): Single<SubscriptionScreen> {
        val cachedScreen = loadedScreens.values.firstOrNull {
            it.id == id
        }?.let {
            SubscriptionScreen(
                id = it.id,
                name = it.name,
                screenHtml = it.screenHtml
            )
        }
        val cachedMaybe = cachedScreen?.let {
            Maybe.just(it)
        } ?: Maybe.empty()
        return cachedMaybe
            .switchIfEmpty(getFallbackScreen().toMaybe())
            .toSingle()
    }

    override fun getFallbackScreen(): Single<SubscriptionScreen> =
        fileStore.getSubscriptionScreen()

    override suspend fun getProductsDetails(requests: Map<String, List<String>>): List<ProductDetails> =
        googleStore.getProductsDetails(requests)

    override fun getSubscriptionState(): Single<SubscriptionState> =
        deviceDao.requireUserId()
            .flatMap { restStore.getSubscriptionState(it) }

    override fun fetchHistory(): Completable {
        return googleStore.fetchHistory()
            .doOnComplete {
                Timber.d("fetchHistory success")
            }.doOnError {
                Timber.e(it)
            }
    }

    private fun acknowledge() {
        googleStore.acknowledge()
            .subscribe(DefaultCompletableObserver())
    }

    private fun saveGooglePurchases(): Completable {

        //active subscriptions from billing client
        return googleStore.getPurchases()
            .flatMap { purchases ->
                intentValidator.validateIntent()
                    .toSingle { purchases }
                    .onErrorReturnItem(emptyList())
            }
            .doOnSuccess { purchases ->
                localStore.savePurchases(purchases)
            }.ignoreElement()
    }

    private fun loadSubscriptionScreen(id: String): Single<ScreenData> {
        Timber.d("loadSubscriptionScreen $id")
        return restStore.getSubscriptionScreen(
            id = id,
        ).doOnSuccess {
            loadedScreens[id] = it
        }
    }
}
