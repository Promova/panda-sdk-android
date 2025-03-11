package com.appsci.panda.sdk

import android.annotation.SuppressLint
import android.app.Application
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.appsci.panda.sdk.domain.subscriptions.*
import com.appsci.panda.sdk.domain.utils.rx.DefaultSchedulerProvider
import com.appsci.panda.sdk.domain.utils.rx.Schedulers
import com.appsci.panda.sdk.injection.components.DaggerPandaComponent
import com.appsci.panda.sdk.injection.components.PandaComponent
import com.appsci.panda.sdk.injection.modules.AppModule
import com.appsci.panda.sdk.injection.modules.BillingModule
import com.appsci.panda.sdk.injection.modules.NetworkModule
import com.appsci.panda.sdk.ui.ScreenExtra
import com.jakewharton.threetenabp.AndroidThreeTen
import dagger.Lazy
import io.reactivex.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.withContext
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Inject
import com.android.billingclient.api.Purchase as GooglePurchase

object Panda {

    @Volatile
    private var initialized: Boolean = false

    private lateinit var panda: IPanda
    private lateinit var context: Application
    internal lateinit var pandaComponent: PandaComponent

    private val dismissListeners: MutableList<() -> Unit> = mutableListOf()
    private val errorListeners: MutableList<(t: Throwable) -> Unit> = mutableListOf()
    private val purchaseListeners: MutableList<(id: String) -> Unit> = mutableListOf()
    private val restoreListeners: MutableList<(ids: List<String>) -> Unit> = mutableListOf()

    private val pandaListeners: MutableList<PandaListener> = mutableListOf()
    private val analyticsListeners: MutableList<PandaAnalyticsListener> = mutableListOf()

    /**
     * Call this function on App start to configure Panda SDK
     */
    @JvmStatic
    fun initialize(
        context: Application,
        apiKey: String,
        debug: Boolean = BuildConfig.DEBUG,
        networkLogLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BASIC,
    ) {
        initializeInternal(context, apiKey, debug, networkLogLevel)
    }

    /**
     * @returns
     */
    val pandaUserId: String?
        get() = panda.pandaUserId

    @JvmStatic
    suspend fun clearAdvId() = withContext(Dispatchers.IO) {
        panda.clearAdvId()
            .subscribeOn(Schedulers.io())
            .await()
    }

    @JvmStatic
    suspend fun syncUser(): String = withContext(Dispatchers.IO) {
        panda.authorize()
            .subscribeOn(Schedulers.io())
            .await()
    }

    @JvmStatic
    suspend fun setFbIds(
        fbc: String?,
        fbp: String?,
    ) = withContext(Dispatchers.IO) {
        panda.setFbIds(fbc = fbc, fbp = fbp)
            .subscribeOn(Schedulers.io())
            .await()
    }


    @JvmStatic
    suspend fun setProperty(
        key: String,
        value: String,
    ) = withContext(Dispatchers.IO) {
        panda.setUserProperty(key = key, value = value)
    }

    @JvmStatic
    suspend fun setProperties(
        map: Map<String, String>,
    ) = withContext(Dispatchers.IO) {
        panda.setUserProperties(map)
    }

    @JvmStatic
    suspend fun sendFeedback(
        screenId: String,
        answer: String,
    ) = withContext(Dispatchers.IO) {
        panda.sendFeedback(screenId = screenId, answer = answer)
    }

    /**
     * Set custom user id to current user
     * @param id - your custom userId,
     */
    @JvmStatic
    fun saveCustomUserId(id: String?) {
        panda.saveCustomUserId(id)
    }

    @JvmStatic
    fun saveLoginData(
        loginData: LoginData,
    ) = panda.saveLoginData(loginData)

    /**
     * Set appsflyer id to current user
     * @param id - your appsflyer Id,
     */
    @JvmStatic
    fun saveAppsflyerId(id: String?) {
        id?.let {
            panda.saveAppsflyerId(id)
        }
    }

    /**
     * Gets subscriptions from google and sends to Panda server
     */
    @JvmStatic
    suspend fun syncSubscriptions() = withContext(Dispatchers.IO) {
        panda.syncSubscriptions()
            .subscribeOn(Schedulers.io())
            .await()
    }

    /**
     * Get user's subscription state from Panda server
     */
    @JvmStatic
    suspend fun getSubscriptionState(): SubscriptionState = withContext(Dispatchers.IO) {
        panda.getSubscriptionState()
            .subscribeOn(Schedulers.io())
            .await()
    }

    /**
     * Consume all products owned by user
     */
    @JvmStatic
    suspend fun consumeProducts() = withContext(Dispatchers.IO) {
        panda.consumeProducts()
            .subscribeOn(Schedulers.io())
            .await()
    }

    /**
     * Get subscription screen and save it to memory cache
     */
    @SuppressLint("SetJavaScriptEnabled")
    @JvmStatic
    suspend fun prefetchSubscriptionScreen(id: String) =
        withContext(Dispatchers.IO) {
            panda.prefetchSubscriptionScreen(id)
                .subscribeOn(Schedulers.io())
                .ignoreElement()
                .await()
        }

    @JvmStatic
    fun getCachedSubscriptionScreen(
        id: String,
    ) = panda.getCachedSubscriptionScreen(id = id)

    @JvmStatic
    suspend fun getSubscriptionScreen(
        id: String,
    ): SubscriptionScreen = withContext(Dispatchers.IO) {
        panda.getSubscriptionScreen(id)
            .subscribeOn(Schedulers.io())
            .await()
    }

    @JvmStatic
    suspend fun getCachedOrDefaultSubscriptionScreen(
        id: String,
    ): SubscriptionScreen = panda.getCachedOrDefaultSubscriptionScreen(id).await()

    @JvmStatic
    suspend fun getProductsDetails(requests: Map<String, List<String>>): List<ProductDetails> =
        withContext(Dispatchers.IO) {
            panda.getProductsDetails(requests)
        }

    @JvmStatic
    suspend fun dropData() = withContext(Dispatchers.IO) {
        panda.stopNetwork()
            .andThen(panda.clearLocalData())
            .await()
    }

    fun addDismissListener(onDismiss: () -> Unit) {
        dismissListeners.add(onDismiss)
    }

    fun removeDismissListener(onDismiss: () -> Unit) {
        dismissListeners.remove(onDismiss)
    }

    fun addErrorListener(onError: (e: Throwable) -> Unit) {
        errorListeners.add(onError)
    }

    fun removeErrorListener(onError: (e: Throwable) -> Unit) {
        errorListeners.remove(onError)
    }

    fun addPurchaseListener(onPurchase: (id: String) -> Unit) {
        purchaseListeners.add(onPurchase)
    }

    fun removePurchaseListener(onPurchase: (id: String) -> Unit) {
        purchaseListeners.remove(onPurchase)
    }

    fun addRestoreListener(onRestore: (ids: List<String>) -> Unit) {
        restoreListeners.add(onRestore)
    }

    fun removeRestoreListener(onRestore: (ids: List<String>) -> Unit) {
        restoreListeners.remove(onRestore)
    }

    fun addListener(listener: PandaListener) {
        pandaListeners.add(listener)
    }

    fun removeListener(listener: PandaListener) {
        pandaListeners.remove(listener)
    }

    fun addAnalyticsListener(listener: PandaAnalyticsListener) {
        analyticsListeners.add(listener)
    }

    fun removeAnalyticsListener(listener: PandaAnalyticsListener) {
        analyticsListeners.remove(listener)
    }

    internal fun onDismiss(screen: ScreenExtra) {
        dismissListeners.forEach { it() }
        pandaListeners.forEach { it.onDismissClick() }
        analyticsListeners.forEach {
            it(
                PandaEvent.DismissClick(
                    screenId = screen.id,
                    screenName = screen.name,
                )
            )
        }
    }

    internal fun onShowCloseConfirmation(screenExtra: ScreenExtra) {
        pandaListeners.forEach { it.onShowCloseConfirmation() }
        analyticsListeners.forEach {
            it(
                PandaEvent.ShowCloseConfirmation(
                    screenId = screenExtra.id,
                    screenName = screenExtra.name,
                )
            )
        }
    }

    internal fun onBackClick(screen: ScreenExtra) {
        pandaListeners.forEach { it.onBackClick() }
        analyticsListeners.forEach {
            it(
                PandaEvent.BackClick(
                    screenId = screen.id,
                    screenName = screen.name,
                )
            )
        }
    }

    internal fun onTermsClick() {
        analyticsListeners.forEach { it(PandaEvent.TermsClick) }
    }

    internal fun onPolicyClick() {
        analyticsListeners.forEach { it(PandaEvent.PolicyClick) }
    }

    internal fun onOpenExternal(screenId: String, url: String) {
        analyticsListeners.forEach { it(PandaEvent.OpenExternal(screenId, url)) }
    }

    internal fun onRedirect(screenId: String, url: String) {
        analyticsListeners.forEach { it(PandaEvent.Redirect(screenId, url)) }
    }

    internal fun onCustomEvent(screenId: String, name: String, params: Map<String, String>) {
        analyticsListeners.forEach {
            it(
                PandaEvent.CustomEvent(
                    name = name,
                    screenId = screenId,
                    params = params,
                )
            )
        }
    }

    fun onAction(name: String, json: String) {
        analyticsListeners.forEach {
            it(
                PandaEvent.Action(
                    name = name,
                    json = json,
                )
            )
        }
    }

    fun onScreenChanged(id: String, screenName: String) {
        analyticsListeners.forEach {
            it(
                PandaEvent.ScreenChanged(
                    screenId = id,
                    screenName = screenName,
                )
            )
        }
    }

    internal suspend fun restore(screenExtra: ScreenExtra): List<String> {
        try {
            val ids = restore()
            notifyRestore(ids)
            if (ids.isNotEmpty()) {
                notifyPurchase(screenExtra, ids.first())
            }
            return ids
        } catch (e: Throwable) {
            notifyError(e)
            throw e
        }
    }

    suspend fun restore(): List<String> =
        panda.restore()
            .subscribeOn(Schedulers.io())
            .await()

    internal fun onPurchase(
        screenExtra: ScreenExtra,
        purchase: GooglePurchase,
        @BillingClient.SkuType type: String,
    ): Single<Boolean> {
        val purchaseType = when (type) {
            BillingClient.SkuType.SUBS -> SkuType.SUBSCRIPTION
            else -> SkuType.INAPP
        }
        return panda.validatePurchase(
            Purchase(
                id = purchase.skus.first(),
                type = purchaseType,
                orderId = purchase.orderId,
                token = purchase.purchaseToken
            )
        )
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.mainThread())
            .doOnError { t ->
                notifyError(t)
            }.doOnSuccess {
                notifyPurchase(screenExtra, purchase.skus.first())
            }
    }

    internal fun onError(throwable: Throwable) {
        notifyError(throwable)
    }

    internal fun screenShowed(screenExtra: ScreenExtra) {
        analyticsListeners.forEach {
            it(
                PandaEvent.ScreenShowed(
                    screenId = screenExtra.id,
                    screenName = screenExtra.name,
                )
            )
        }
    }

    internal fun subscriptionSelect(screenExtra: ScreenExtra, id: String) {
        analyticsListeners.forEach {
            it(
                PandaEvent.SubscriptionSelect(
                    productId = id,
                    screenId = screenExtra.id,
                    screenName = screenExtra.name,
                )
            )
        }
    }

    private fun notifyError(throwable: Throwable) {
        errorListeners.forEach { it(throwable) }
        pandaListeners.forEach { it.onError(throwable) }
    }

    private fun notifyPurchase(
        screenExtra: ScreenExtra,
        skuId: String,
    ) {
        pandaListeners.forEach { it.onPurchase(skuId) }
        analyticsListeners.forEach {
            it(
                PandaEvent.SuccessfulPurchase(
                    screenId = screenExtra.id,
                    screenName = screenExtra.name,
                    productId = skuId,
                )
            )
        }
        purchaseListeners.forEach { it(skuId) }
    }

    private fun notifyRestore(ids: List<String>) {
        restoreListeners.forEach { it(ids) }
        pandaListeners.forEach { it.onRestore(ids) }
    }

    private fun initializeInternal(
        context: Application,
        apiKey: String,
        debug: Boolean = BuildConfig.DEBUG,
        networkLogLevel: HttpLoggingInterceptor.Level,
    ) {
        if (initialized) return
        this.context = context
        Schedulers.setInstance(DefaultSchedulerProvider())
        AndroidThreeTen.init(context)
        val wrapper = PandaDependencies()
        pandaComponent = DaggerPandaComponent
            .builder()
            .appModule(AppModule(context.applicationContext))
            .billingModule(BillingModule(context))
            .networkModule(
                NetworkModule(
                    debug = debug,
                    apiKey = apiKey,
                    networkLogLevel = networkLogLevel,
                )
            )
            .build()
        pandaComponent.inject(wrapper)
        panda = wrapper.panda
        panda.onStart()
        initialized = true
    }

}

class PandaDependencies {
    @Inject
    lateinit var pandaLazy: Lazy<IPanda>
    val panda: IPanda
        get() = pandaLazy.get()
}

