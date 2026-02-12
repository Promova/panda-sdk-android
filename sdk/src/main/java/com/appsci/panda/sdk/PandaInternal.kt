package com.appsci.panda.sdk

import com.android.billingclient.api.ProductDetails
import com.appsci.panda.sdk.data.StopNetwork
import com.appsci.panda.sdk.domain.device.DeviceRepository
import com.appsci.panda.sdk.domain.feedback.FeedbackRepository
import com.appsci.panda.sdk.domain.subscriptions.Purchase
import com.appsci.panda.sdk.domain.subscriptions.SubscriptionScreen
import com.appsci.panda.sdk.domain.subscriptions.SubscriptionState
import com.appsci.panda.sdk.domain.subscriptions.SubscriptionsRepository
import com.appsci.panda.sdk.domain.utils.DeviceManager
import com.appsci.panda.sdk.domain.utils.LocalPropertiesDataSource
import com.appsci.panda.sdk.domain.utils.Preferences
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber

interface IPanda {
    val pandaUserId: String?

    fun onStart()
    suspend fun authorize(): String
    suspend fun clearAdvId()
    suspend fun syncSubscriptions()
    suspend fun validatePurchase(purchase: Purchase): Boolean
    suspend fun restore(): List<String>
    suspend fun getSubscriptionState(): SubscriptionState
    suspend fun prefetchSubscriptionScreen(
        id: String,
    ): SubscriptionScreen

    suspend fun getSubscriptionScreen(
        id: String,
        timeoutMs: Long = 5000L,
    ): SubscriptionScreen

    fun getCachedSubscriptionScreen(
        id: String,
    ): SubscriptionScreen?

    suspend fun getCachedOrDefaultSubscriptionScreen(
        id: String,
    ): SubscriptionScreen

    suspend fun consumeProducts()
    suspend fun setAppsflyerId(id: String)
    suspend fun setFbIds(fbc: String?, fbp: String?)
    fun saveLoginData(loginData: LoginData)
    fun saveCustomUserId(id: String?)
    suspend fun setUserProperty(key: String, value: String)
    suspend fun setUserProperties(map: Map<String, String>)
    suspend fun getProductsDetails(requests: Map<String, List<String>>): List<ProductDetails>
    suspend fun sendFeedback(screenId: String, answer: String)

    /**
     * save appsflyer id in local storage, will be used in next update request
     */
    fun saveAppsflyerId(id: String)
    fun stopNetwork()
    suspend fun clearLocalData()
}

class PandaImpl(
    private val preferencesLazy: Lazy<Preferences>,
    private val deviceManagerLazy: Lazy<DeviceManager>,
    private val deviceRepositoryLazy: Lazy<DeviceRepository>,
    private val subscriptionsRepositoryLazy: Lazy<SubscriptionsRepository>,
    private val stopNetworkInternalLazy: Lazy<StopNetwork>,
    private val propertiesDataSourceLazy: Lazy<LocalPropertiesDataSource>,
    private val feedbackRepositoryLazy: Lazy<FeedbackRepository>,
) : IPanda {

    private val preferences: Preferences
        get() = preferencesLazy.get()

    private val deviceManager: DeviceManager
        get() = deviceManagerLazy.get()

    private val deviceRepository: DeviceRepository
        get() = deviceRepositoryLazy.get()

    private val subscriptionsRepository: SubscriptionsRepository
        get() = subscriptionsRepositoryLazy.get()

    private val stopNetworkInternal: StopNetwork
        get() = stopNetworkInternalLazy.get()

    private val propertiesDataSource: LocalPropertiesDataSource
        get() = propertiesDataSourceLazy.get()

    private val feedbackRepository: FeedbackRepository
        get() = feedbackRepositoryLazy.get()

    override val pandaUserId: String?
        get() = deviceRepository.pandaUserId

    override fun onStart() {
        if (preferences.startVersion.isNullOrEmpty()) {
            preferences.startVersion = deviceManager.getAppVersionName()
        }
    }

    override suspend fun authorize(): String =
        deviceRepository.authorize().id

    override fun saveCustomUserId(id: String?) {
        if (preferences.customUserId == id) return
        preferences.customUserId = id
    }

    override suspend fun setUserProperty(key: String, value: String) {
        propertiesDataSource.putProperty(key, value)
        withContext(Dispatchers.IO) {
            try {
                deviceRepository.authorize()
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun setUserProperties(map: Map<String, String>) {
        map.forEach { (key, value) ->
            propertiesDataSource.putProperty(key, value)
        }
        withContext(Dispatchers.IO) {
            try {
                deviceRepository.authorize()
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun getProductsDetails(requests: Map<String, List<String>>): List<ProductDetails> =
        subscriptionsRepository.getProductsDetails(requests)

    override suspend fun sendFeedback(screenId: String, answer: String) {
        deviceRepository.ensureAuthorized()
        feedbackRepository.sendFeedback(screenId = screenId, answer = answer)
    }

    override suspend fun clearAdvId() {
        deviceRepository.clearAdvId()
    }

    override suspend fun setAppsflyerId(id: String) {
        if (preferences.appsflyerId == id) return
        preferences.appsflyerId = id
        try {
            deviceRepository.authorize()
        } catch (_: Exception) {
        }
    }

    override suspend fun setFbIds(fbc: String?, fbp: String?) {
        if (preferences.fbc == fbc && preferences.fbp == fbp) return
        preferences.fbc = fbc
        preferences.fbp = fbp
        try {
            deviceRepository.ensureAuthorized()
            deviceRepository.authorize()
        } catch (_: Exception) {
        }
    }

    override fun saveLoginData(loginData: LoginData) {
        val current = LoginData(
            email = preferences.email,
            facebookLoginId = preferences.facebookLoginId,
            firstName = preferences.firstName,
            lastName = preferences.lastName,
            fullName = preferences.fullName,
            gender = preferences.gender,
            phone = preferences.phone
        )
        if (loginData == current) return
        preferences.apply {
            facebookLoginId = loginData.facebookLoginId
            email = loginData.email
            firstName = loginData.firstName
            lastName = loginData.lastName
            fullName = loginData.fullName
            gender = loginData.gender
            phone = loginData.phone
        }
    }

    override fun saveAppsflyerId(id: String) {
        preferences.appsflyerId = id
    }

    override fun stopNetwork() = stopNetworkInternal()

    override suspend fun clearLocalData() = deviceRepository.clearLocalData()

    override suspend fun syncSubscriptions() {
        deviceRepository.ensureAuthorized()
        subscriptionsRepository.sync()
    }

    override suspend fun validatePurchase(purchase: Purchase): Boolean {
        deviceRepository.ensureAuthorized()
        return subscriptionsRepository.validatePurchase(purchase)
    }

    override suspend fun restore(): List<String> {
        deviceRepository.ensureAuthorized()
        return subscriptionsRepository.restore()
    }

    override suspend fun getSubscriptionState(): SubscriptionState {
        deviceRepository.ensureAuthorized()
        return subscriptionsRepository.getSubscriptionState()
    }

    override suspend fun prefetchSubscriptionScreen(
        id: String,
    ): SubscriptionScreen {
        deviceRepository.ensureAuthorized()
        return subscriptionsRepository.prefetchSubscriptionScreen(id)
    }

    override suspend fun getSubscriptionScreen(
        id: String,
        timeoutMs: Long,
    ): SubscriptionScreen {
        return try {
            withTimeout(timeoutMs) {
                deviceRepository.ensureAuthorized()
                subscriptionsRepository.getSubscriptionScreen(id)
            }
        } catch (e: Exception) {
            Timber.e(e, "getSubscriptionScreen")
            subscriptionsRepository.getFallbackScreen()
        }
    }

    override fun getCachedSubscriptionScreen(id: String): SubscriptionScreen? =
        subscriptionsRepository.getCachedScreen(id = id)

    override suspend fun getCachedOrDefaultSubscriptionScreen(
        id: String,
    ): SubscriptionScreen =
        subscriptionsRepository.getCachedScreen(id)
            ?: subscriptionsRepository.getFallbackScreen()

    override suspend fun consumeProducts() {
        deviceRepository.ensureAuthorized()
        subscriptionsRepository.consumeProducts()
    }

}

data class LoginData(
    val email: String? = null,
    val facebookLoginId: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val fullName: String? = null,
    val gender: Int? = null,
    val phone: String? = null,
)
