package com.appsci.panda.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import com.android.billingclient.api.BillingClient
import com.appsci.panda.sdk.domain.subscriptions.Purchase
import com.appsci.panda.sdk.domain.subscriptions.ScreenType
import com.appsci.panda.sdk.domain.subscriptions.SkuType
import com.appsci.panda.sdk.domain.subscriptions.SubscriptionState
import com.appsci.panda.sdk.domain.utils.rx.DefaultCompletableObserver
import com.appsci.panda.sdk.domain.utils.rx.DefaultSchedulerProvider
import com.appsci.panda.sdk.domain.utils.rx.DefaultSingleObserver
import com.appsci.panda.sdk.domain.utils.rx.Schedulers
import com.appsci.panda.sdk.injection.components.DaggerPandaComponent
import com.appsci.panda.sdk.injection.components.PandaComponent
import com.appsci.panda.sdk.injection.modules.AppModule
import com.appsci.panda.sdk.injection.modules.BillingModule
import com.appsci.panda.sdk.injection.modules.NetworkModule
import com.appsci.panda.sdk.ui.ScreenExtra
import com.appsci.panda.sdk.ui.SubscriptionActivity
import com.appsci.panda.sdk.ui.SubscriptionFragment
import com.jakewharton.threetenabp.AndroidThreeTen
import io.reactivex.Completable
import io.reactivex.Single
import timber.log.Timber
import javax.inject.Inject
import com.android.billingclient.api.Purchase as GooglePurchase

object Panda {

    private lateinit var panda: IPanda
    private lateinit var context: Context
    internal lateinit var pandaComponent: PandaComponent

    private val dismissListeners: MutableList<() -> Unit> = mutableListOf()
    private val errorListeners: MutableList<(t: Throwable) -> Unit> = mutableListOf()
    private val purchaseListeners: MutableList<(id: String) -> Unit> = mutableListOf()
    private val restoreListeners: MutableList<(ids: List<String>) -> Unit> = mutableListOf()

    @kotlin.jvm.JvmStatic
    fun configure(
            context: Context,
            apiKey: String,
            debug: Boolean = BuildConfig.DEBUG,
            onSuccess: ((String) -> Unit)? = null,
            onError: ((Throwable) -> Unit)? = null
    ) = configure(context, apiKey, debug)
            .doOnSuccess { onSuccess?.invoke(it) }
            .doOnError { onError?.invoke(it) }
            .subscribe(DefaultSingleObserver())

    @kotlin.jvm.JvmStatic
    fun configure(
            context: Context,
            apiKey: String,
            debug: Boolean = BuildConfig.DEBUG
    ): Single<String> {
        this.context = context
        Schedulers.setInstance(DefaultSchedulerProvider())
        AndroidThreeTen.init(context)
        val wrapper = PandaDependencies()
        pandaComponent = DaggerPandaComponent
                .builder()
                .appModule(AppModule(context.applicationContext))
                .billingModule(BillingModule(context))
                .networkModule(NetworkModule(
                        debug = debug,
                        apiKey = apiKey
                ))
                .build()
        pandaComponent.inject(wrapper)
        panda = wrapper.panda
        panda.start()

        panda.syncSubscriptions()
                .observeOn(Schedulers.mainThread())
                .subscribe(DefaultCompletableObserver())

        return panda.authorize()
                .subscribeOn(Schedulers.io())
                .doOnSuccess {
                    Timber.d("authorize success")
                }
                .observeOn(Schedulers.mainThread())
    }

    @kotlin.jvm.JvmStatic
    fun setCustomUserId(id: String,
                        onComplete: (() -> Unit)? = null,
                        onError: ((Throwable) -> Unit)? = null
    ) = setCustomUserIdRx(id)
            .doOnComplete { onComplete?.invoke() }
            .doOnError { onError?.invoke(it) }
            .subscribe(DefaultCompletableObserver())

    @kotlin.jvm.JvmStatic
    fun setCustomUserIdRx(id: String): Completable =
            panda.setCustomUserId(id)
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.mainThread())

    @kotlin.jvm.JvmStatic
    fun syncSubscriptions(
            onComplete: (() -> Unit)? = null,
            onError: ((Throwable) -> Unit)? = null
    ) = syncSubscriptionsRx()
            .doOnComplete { onComplete?.invoke() }
            .doOnError { onError?.invoke(it) }
            .subscribe(DefaultCompletableObserver())

    @kotlin.jvm.JvmStatic
    fun syncSubscriptionsRx(): Completable =
            panda.syncSubscriptions()
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.mainThread())

    @kotlin.jvm.JvmStatic
    fun getSubscriptionState(
            onSuccess: ((SubscriptionState) -> Unit)? = null,
            onError: ((Throwable) -> Unit)? = null
    ): Unit = getSubscriptionStateRx()
            .doOnSuccess { onSuccess?.invoke(it) }
            .doOnError { onError?.invoke(it) }
            .subscribe(DefaultSingleObserver())

    @kotlin.jvm.JvmStatic
    fun getSubscriptionStateRx(): Single<SubscriptionState> =
            panda.getSubscriptionState()
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.mainThread())

    @kotlin.jvm.JvmStatic
    fun prefetchSubscriptionScreen(
            type: ScreenType = ScreenType.Sales,
            id: String? = null,
            onComplete: (() -> Unit)? = null,
            onError: ((Throwable) -> Unit)? = null
    ) = prefetchSubscriptionScreenRx(type, id)
            .doOnComplete { onComplete?.invoke() }
            .doOnError { onError?.invoke(it) }
            .subscribe(DefaultCompletableObserver())

    @kotlin.jvm.JvmStatic
    fun prefetchSubscriptionScreenRx(type: ScreenType = ScreenType.Sales, id: String? = null) =
            panda.prefetchSubscriptionScreen(type, id)
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.mainThread())

    @kotlin.jvm.JvmStatic
    fun getSubscriptionScreen(
            type: ScreenType? = null,
            id: String? = null,
            onSuccess: ((Fragment) -> Unit)? = null,
            onError: ((Throwable) -> Unit)? = null
    ) = getSubscriptionScreenRx(type, id)
            .doOnSuccess { onSuccess?.invoke(it) }
            .doOnError { onError?.invoke(it) }
            .subscribe(DefaultSingleObserver())

    @kotlin.jvm.JvmStatic
    fun getSubscriptionScreenRx(type: ScreenType? = null, id: String? = null): Single<Fragment> =
            panda.getSubscriptionScreen(type, id)
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.mainThread())
                    .map { SubscriptionFragment.create(ScreenExtra.create(it)) }

    @kotlin.jvm.JvmStatic
    fun showSubscriptionScreen(
            type: ScreenType? = null,
            id: String? = null,
            activity: Activity? = null,
            theme: Int? = null,
            onComplete: (() -> Unit)? = null,
            onError: ((Throwable) -> Unit)? = null
    ) = showSubscriptionScreenRx(type, id, activity, theme)
            .doOnComplete { onComplete?.invoke() }
            .doOnError { onError?.invoke(it) }
            .subscribe(DefaultCompletableObserver())

    @kotlin.jvm.JvmStatic
    fun showSubscriptionScreenRx(
            type: ScreenType? = null,
            id: String? = null,
            activity: Activity? = null,
            theme: Int? = null,
    ): Completable =
            panda.getSubscriptionScreen(type, id)
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.mainThread())
                    .doOnSuccess {
                        val launchContext = activity ?: this.context
                        val intent = SubscriptionActivity.createIntent(launchContext, ScreenExtra.create(it), theme)
                        if (activity == null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        launchContext.startActivity(intent)
                    }
                    .ignoreElement()

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

    internal fun onDismiss() {
        dismissListeners.forEach { it() }
    }

    internal fun restore(): Single<List<String>> =
            panda.restore()
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.mainThread())
                    .doOnSuccess { ids ->
                        restoreListeners.forEach { it(ids) }
                        if (ids.isEmpty()) {
                            purchaseListeners.forEach { it(ids.first()) }
                        }
                    }
                    .doOnError { e ->
                        errorListeners.forEach { it(e) }
                    }

    internal fun onPurchase(purchase: GooglePurchase, @BillingClient.SkuType type: String) {
        val purchaseType = when (type) {
            BillingClient.SkuType.SUBS -> SkuType.SUBSCRIPTION
            else -> SkuType.INAPP
        }
        panda.validatePurchase(
                Purchase(
                        id = purchase.sku,
                        type = purchaseType,
                        orderId = purchase.orderId,
                        token = purchase.purchaseToken
                ))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.mainThread())
                .doOnError { t ->
                    errorListeners.forEach { it(t) }
                }.doOnSuccess {
                    purchaseListeners.forEach { it(purchase.sku) }
                }.subscribe(DefaultSingleObserver())
    }

    internal fun onError(throwable: Throwable) {
        errorListeners.forEach { it(throwable) }
    }

}

class PandaDependencies {
    @Inject
    lateinit var panda: IPanda
}

