package com.appsci.panda.sdk.data.subscriptions.google

import com.android.billingclient.api.*
import com.appsci.panda.sdk.data.subscriptions.PurchasesMapper
import com.appsci.panda.sdk.data.subscriptions.local.PurchaseEntity
import com.appsci.panda.sdk.data.subscriptions.local.TYPE_PRODUCT
import com.appsci.panda.sdk.data.subscriptions.local.TYPE_SUBSCRIPTION
import com.appsci.panda.sdk.domain.utils.rx.SchedulerProvider
import com.gen.rxbilling.client.RxBilling
import io.mockk.*
import io.reactivex.Completable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import com.appsci.panda.sdk.domain.utils.rx.Schedulers as AppSchedulers

/**
 * Tests for PurchasesGoogleStore - validates Google Play Billing integration.
 *
 * These tests mock RxBilling to verify:
 * - Correct purchase retrieval for subscriptions and in-app products
 * - Purchase acknowledgment flow
 * - Product consumption flow
 * - Product details retrieval
 *
 * IMPORTANT: When migrating from RxBilling to direct BillingClient,
 * these tests define the expected behavior that must be preserved.
 */
@DisplayName("PurchasesGoogleStore")
@OptIn(ExperimentalCoroutinesApi::class)
class PurchasesGoogleStoreTest {

    private lateinit var rxBilling: RxBilling
    private lateinit var mapper: PurchasesMapper
    private lateinit var store: PurchasesGoogleStoreImpl

    @BeforeEach
    fun setUp() {
        // Override schedulers for synchronous testing
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }

        // Initialize the app's custom Schedulers with trampoline for testing
        AppSchedulers.setInstance(object : SchedulerProvider {
            override fun io(): Scheduler = Schedulers.trampoline()
            override fun mainThread(): Scheduler = Schedulers.trampoline()
            override fun computation(): Scheduler = Schedulers.trampoline()
            override fun newThread(): Scheduler = Schedulers.trampoline()
            override fun trampoline(): Scheduler = Schedulers.trampoline()
        })

        rxBilling = mockk(relaxed = true)
        mapper = mockk(relaxed = true)
        store = PurchasesGoogleStoreImpl(rxBilling, mapper)
    }

    @AfterEach
    fun tearDown() {
        RxJavaPlugins.reset()
        clearAllMocks()
    }

    @Nested
    @DisplayName("getPurchases")
    inner class GetPurchases {

        @Test
        @DisplayName("should fetch and combine subscriptions and in-app purchases")
        fun fetchAndCombinePurchases() {
            // Given
            val subscriptionPurchase = createMockPurchase("sub_monthly", "order_sub", "token_sub")
            val productPurchase = createMockPurchase("coins_100", "order_product", "token_product")

            val subscriptionEntity = createPurchaseEntity("sub_monthly", TYPE_SUBSCRIPTION)
            val productEntity = createPurchaseEntity("coins_100", TYPE_PRODUCT)

            every { rxBilling.getPurchases(BillingClient.SkuType.SUBS) } returns
                Single.just(listOf(subscriptionPurchase))
            every { rxBilling.getPurchases(BillingClient.SkuType.INAPP) } returns
                Single.just(listOf(productPurchase))

            every { mapper.mapFromBillingPurchases(listOf(subscriptionPurchase), TYPE_SUBSCRIPTION) } returns
                listOf(subscriptionEntity)
            every { mapper.mapFromBillingPurchases(listOf(productPurchase), TYPE_PRODUCT) } returns
                listOf(productEntity)

            // When
            val result = store.getPurchases().blockingGet()

            // Then
            assertThat(result).hasSize(2)
            assertThat(result).contains(subscriptionEntity, productEntity)

            // Verify both purchase types were fetched
            verify { rxBilling.getPurchases(BillingClient.SkuType.SUBS) }
            verify { rxBilling.getPurchases(BillingClient.SkuType.INAPP) }
        }

        @Test
        @DisplayName("should return empty list when no purchases exist")
        fun returnEmptyWhenNoPurchases() {
            // Given
            every { rxBilling.getPurchases(BillingClient.SkuType.SUBS) } returns
                Single.just(emptyList())
            every { rxBilling.getPurchases(BillingClient.SkuType.INAPP) } returns
                Single.just(emptyList())

            every { mapper.mapFromBillingPurchases(emptyList(), TYPE_SUBSCRIPTION) } returns emptyList()
            every { mapper.mapFromBillingPurchases(emptyList(), TYPE_PRODUCT) } returns emptyList()

            // When
            val result = store.getPurchases().blockingGet()

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("should handle subscription fetch error gracefully")
        fun handleSubscriptionError() {
            // Given
            val error = RuntimeException("Billing service unavailable")
            every { rxBilling.getPurchases(BillingClient.SkuType.SUBS) } returns Single.error(error)

            // When/Then
            store.getPurchases()
                .test()
                .assertError(error)

            // Verify INAPP was not called since SUBS failed first
            verify { rxBilling.getPurchases(BillingClient.SkuType.SUBS) }
        }

        @Test
        @DisplayName("should handle in-app fetch error after successful subscription fetch")
        fun handleInAppErrorAfterSubscriptionSuccess() {
            // Given
            val subscriptionPurchase = createMockPurchase("sub", "order", "token")
            val subscriptionEntity = createPurchaseEntity("sub", TYPE_SUBSCRIPTION)
            val error = RuntimeException("Network error")

            every { rxBilling.getPurchases(BillingClient.SkuType.SUBS) } returns
                Single.just(listOf(subscriptionPurchase))
            every { rxBilling.getPurchases(BillingClient.SkuType.INAPP) } returns
                Single.error(error)
            every { mapper.mapFromBillingPurchases(listOf(subscriptionPurchase), TYPE_SUBSCRIPTION) } returns
                listOf(subscriptionEntity)

            // When/Then
            store.getPurchases()
                .test()
                .assertError(error)
        }
    }

    @Nested
    @DisplayName("consumeProducts")
    inner class ConsumeProducts {

        @Test
        @DisplayName("should consume all in-app products")
        fun consumeAllProducts() {
            // Given
            val purchase1 = createMockPurchase("coins_100", "order_1", "token_1")
            val purchase2 = createMockPurchase("coins_500", "order_2", "token_2")

            every { rxBilling.getPurchases(BillingClient.SkuType.INAPP) } returns
                Single.just(listOf(purchase1, purchase2))
            every { rxBilling.consumeProduct(any()) } returns Completable.complete()

            // When
            store.consumeProducts()
                .test()
                .assertComplete()

            // Then - verify both products were consumed
            verify(exactly = 2) { rxBilling.consumeProduct(any()) }
        }

        @Test
        @DisplayName("should complete successfully when no products to consume")
        fun completeWhenNoProducts() {
            // Given
            every { rxBilling.getPurchases(BillingClient.SkuType.INAPP) } returns
                Single.just(emptyList())

            // When/Then
            store.consumeProducts()
                .test()
                .assertComplete()

            // Verify consumeProduct was never called
            verify(exactly = 0) { rxBilling.consumeProduct(any()) }
        }

        @Test
        @DisplayName("should propagate error when consumption fails")
        fun propagateConsumptionError() {
            // Given
            val purchase = createMockPurchase("coins", "order", "token")
            val error = RuntimeException("Consumption failed")

            every { rxBilling.getPurchases(BillingClient.SkuType.INAPP) } returns
                Single.just(listOf(purchase))
            every { rxBilling.consumeProduct(any()) } returns Completable.error(error)

            // When/Then
            store.consumeProducts()
                .test()
                .assertError(error)
        }
    }

    @Nested
    @DisplayName("acknowledge")
    inner class Acknowledge {

        @Test
        @DisplayName("should acknowledge only unacknowledged purchases")
        fun acknowledgeUnacknowledgedOnly() {
            // Given
            val acknowledgedPurchase = createMockPurchase("sub_1", "order_1", "token_1", isAcknowledged = true)
            val unacknowledgedPurchase = createMockPurchase("sub_2", "order_2", "token_2", isAcknowledged = false)

            every { rxBilling.getPurchases(BillingClient.SkuType.SUBS) } returns
                Single.just(listOf(acknowledgedPurchase, unacknowledgedPurchase))
            every { rxBilling.getPurchases(BillingClient.SkuType.INAPP) } returns
                Single.just(emptyList())
            every { rxBilling.acknowledge(any()) } returns Completable.complete()

            // When
            store.acknowledge()
                .test()
                .assertComplete()

            // Then - only unacknowledged purchase should be acknowledged
            verify(exactly = 1) { rxBilling.acknowledge(any()) }
        }

        @Test
        @DisplayName("should acknowledge purchases from both subscriptions and in-app")
        fun acknowledgeBothTypes() {
            // Given
            val subPurchase = createMockPurchase("sub", "order_sub", "token_sub", isAcknowledged = false)
            val productPurchase = createMockPurchase("coins", "order_product", "token_product", isAcknowledged = false)

            every { rxBilling.getPurchases(BillingClient.SkuType.SUBS) } returns
                Single.just(listOf(subPurchase))
            every { rxBilling.getPurchases(BillingClient.SkuType.INAPP) } returns
                Single.just(listOf(productPurchase))
            every { rxBilling.acknowledge(any()) } returns Completable.complete()

            // When
            store.acknowledge()
                .test()
                .assertComplete()

            // Then - both should be acknowledged
            verify(exactly = 2) { rxBilling.acknowledge(any()) }
        }

        @Test
        @DisplayName("should complete when all purchases already acknowledged")
        fun completeWhenAllAcknowledged() {
            // Given
            val purchase = createMockPurchase("sub", "order", "token", isAcknowledged = true)

            every { rxBilling.getPurchases(BillingClient.SkuType.SUBS) } returns
                Single.just(listOf(purchase))
            every { rxBilling.getPurchases(BillingClient.SkuType.INAPP) } returns
                Single.just(emptyList())

            // When/Then
            store.acknowledge()
                .test()
                .assertComplete()

            verify(exactly = 0) { rxBilling.acknowledge(any()) }
        }
    }

    @Nested
    @DisplayName("fetchHistory")
    inner class FetchHistory {

        @Test
        @DisplayName("should fetch history for both subscriptions and in-app products")
        fun fetchBothHistories() {
            // Given
            every { rxBilling.getPurchaseHistory(BillingClient.SkuType.SUBS) } returns
                Single.just(emptyList())
            every { rxBilling.getPurchaseHistory(BillingClient.SkuType.INAPP) } returns
                Single.just(emptyList())

            // When
            store.fetchHistory()
                .test()
                .assertComplete()

            // Then
            verify { rxBilling.getPurchaseHistory(BillingClient.SkuType.SUBS) }
            verify { rxBilling.getPurchaseHistory(BillingClient.SkuType.INAPP) }
        }

        @Test
        @DisplayName("should propagate error from subscription history")
        fun propagateSubscriptionHistoryError() {
            // Given
            val error = RuntimeException("History fetch failed")
            every { rxBilling.getPurchaseHistory(BillingClient.SkuType.SUBS) } returns
                Single.error(error)

            // When/Then
            store.fetchHistory()
                .test()
                .assertError(error)
        }
    }

    @Nested
    @DisplayName("getProductsDetails")
    inner class GetProductsDetails {

        @Test
        @DisplayName("should fetch product details for subscription type")
        fun fetchSubscriptionDetails() = runTest {
            // Given
            val productDetails = createMockProductDetails("premium_monthly", BillingClient.ProductType.SUBS)
            val requests = mapOf(BillingClient.ProductType.SUBS to listOf("premium_monthly"))

            every { rxBilling.getProductDetails(any()) } returns Single.just(listOf(productDetails))

            // When
            val result = store.getProductsDetails(requests)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result.first().productId).isEqualTo("premium_monthly")
        }

        @Test
        @DisplayName("should return empty list for empty requests")
        fun returnEmptyForEmptyRequests() = runTest {
            // When
            val result = store.getProductsDetails(emptyMap())

            // Then
            assertThat(result).isEmpty()
            verify(exactly = 0) { rxBilling.getProductDetails(any()) }
        }
    }

    // Helper functions

    private fun createMockPurchase(
        productId: String,
        orderId: String,
        purchaseToken: String,
        isAcknowledged: Boolean = false
    ): Purchase = mockk {
        every { skus } returns ArrayList(listOf(productId))
        every { products } returns ArrayList(listOf(productId))
        every { this@mockk.orderId } returns orderId
        every { this@mockk.purchaseToken } returns purchaseToken
        every { this@mockk.isAcknowledged } returns isAcknowledged
    }

    private fun createPurchaseEntity(
        productId: String,
        type: Int,
        synced: Boolean = false
    ) = PurchaseEntity(
        productId = productId,
        orderId = "order_$productId",
        purchaseToken = "token_$productId",
        purchaseType = type,
        synced = synced
    )

    private fun createMockProductDetails(
        productId: String,
        productType: String
    ): ProductDetails = mockk {
        every { this@mockk.productId } returns productId
        every { this@mockk.productType } returns productType
    }
}
