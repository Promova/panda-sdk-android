package com.appsci.panda.sdk.data.subscriptions.google

import com.android.billingclient.api.*
import com.appsci.billingktx.client.BillingKtx
import com.appsci.panda.sdk.data.subscriptions.PurchasesMapper
import com.appsci.panda.sdk.data.subscriptions.local.PurchaseEntity
import com.appsci.panda.sdk.data.subscriptions.local.TYPE_PRODUCT
import com.appsci.panda.sdk.data.subscriptions.local.TYPE_SUBSCRIPTION
import com.appsci.panda.sdk.domain.utils.rx.SchedulerProvider
import io.mockk.*
import io.reactivex.Scheduler
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import com.appsci.panda.sdk.domain.utils.rx.Schedulers as AppSchedulers

/**
 * Tests for PurchasesGoogleStore - validates Google Play Billing integration.
 *
 * These tests mock BillingKtx to verify:
 * - Correct purchase retrieval for subscriptions and in-app products
 * - Purchase acknowledgment flow
 * - Product consumption flow
 * - Product details retrieval
 */
@DisplayName("PurchasesGoogleStore")
class PurchasesGoogleStoreTest {

    private lateinit var billingKtx: BillingKtx
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

        billingKtx = mockk(relaxed = true)
        mapper = mockk(relaxed = true)
        store = PurchasesGoogleStoreImpl(billingKtx, mapper)
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
        fun fetchAndCombinePurchases() = runBlocking {
            // Given
            val subscriptionPurchase = createMockPurchase("sub_monthly", "order_sub", "token_sub")
            val productPurchase = createMockPurchase("coins_100", "order_product", "token_product")

            val subscriptionEntity = createPurchaseEntity("sub_monthly", TYPE_SUBSCRIPTION)
            val productEntity = createPurchaseEntity("coins_100", TYPE_PRODUCT)

            coEvery { billingKtx.getPurchases(BillingClient.ProductType.SUBS) } returns
                listOf(subscriptionPurchase)
            coEvery { billingKtx.getPurchases(BillingClient.ProductType.INAPP) } returns
                listOf(productPurchase)

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
            coVerify { billingKtx.getPurchases(BillingClient.ProductType.SUBS) }
            coVerify { billingKtx.getPurchases(BillingClient.ProductType.INAPP) }
        }

        @Test
        @DisplayName("should return empty list when no purchases exist")
        fun returnEmptyWhenNoPurchases() = runBlocking {
            // Given
            coEvery { billingKtx.getPurchases(BillingClient.ProductType.SUBS) } returns emptyList()
            coEvery { billingKtx.getPurchases(BillingClient.ProductType.INAPP) } returns emptyList()

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
            coEvery { billingKtx.getPurchases(BillingClient.ProductType.SUBS) } throws error

            // When/Then
            store.getPurchases()
                .test()
                .await()
                .assertError(error)
        }

        @Test
        @DisplayName("should handle in-app fetch error after successful subscription fetch")
        fun handleInAppErrorAfterSubscriptionSuccess() {
            // Given
            val subscriptionPurchase = createMockPurchase("sub", "order", "token")
            val subscriptionEntity = createPurchaseEntity("sub", TYPE_SUBSCRIPTION)
            val error = RuntimeException("Network error")

            coEvery { billingKtx.getPurchases(BillingClient.ProductType.SUBS) } returns
                listOf(subscriptionPurchase)
            coEvery { billingKtx.getPurchases(BillingClient.ProductType.INAPP) } throws error
            every { mapper.mapFromBillingPurchases(listOf(subscriptionPurchase), TYPE_SUBSCRIPTION) } returns
                listOf(subscriptionEntity)

            // When/Then
            store.getPurchases()
                .test()
                .await()
                .assertError(error)
        }
    }

    @Nested
    @DisplayName("consumeProducts")
    inner class ConsumeProducts {

        @Test
        @DisplayName("should consume all in-app products")
        fun consumeAllProducts() = runBlocking {
            // Given
            val purchase1 = createMockPurchase("coins_100", "order_1", "token_1")
            val purchase2 = createMockPurchase("coins_500", "order_2", "token_2")

            coEvery { billingKtx.getPurchases(BillingClient.ProductType.INAPP) } returns
                listOf(purchase1, purchase2)
            coEvery { billingKtx.consumeProduct(any()) } just Runs

            // When
            store.consumeProducts()
                .test()
                .await()
                .assertComplete()

            // Then - verify both products were consumed
            coVerify(exactly = 2) { billingKtx.consumeProduct(any()) }
        }

        @Test
        @DisplayName("should complete successfully when no products to consume")
        fun completeWhenNoProducts() = runBlocking {
            // Given
            coEvery { billingKtx.getPurchases(BillingClient.ProductType.INAPP) } returns emptyList()

            // When/Then
            store.consumeProducts()
                .test()
                .await()
                .assertComplete()

            // Verify consumeProduct was never called
            coVerify(exactly = 0) { billingKtx.consumeProduct(any()) }
        }

        @Test
        @DisplayName("should propagate error when consumption fails")
        fun propagateConsumptionError() {
            // Given
            val purchase = createMockPurchase("coins", "order", "token")
            val error = RuntimeException("Consumption failed")

            coEvery { billingKtx.getPurchases(BillingClient.ProductType.INAPP) } returns listOf(purchase)
            coEvery { billingKtx.consumeProduct(any()) } throws error

            // When/Then
            store.consumeProducts()
                .test()
                .await()
                .assertError(error)
        }
    }

    @Nested
    @DisplayName("acknowledge")
    inner class Acknowledge {

        @Test
        @DisplayName("should acknowledge only unacknowledged purchases")
        fun acknowledgeUnacknowledgedOnly() = runBlocking {
            // Given
            val acknowledgedPurchase = createMockPurchase("sub_1", "order_1", "token_1", isAcknowledged = true)
            val unacknowledgedPurchase = createMockPurchase("sub_2", "order_2", "token_2", isAcknowledged = false)

            coEvery { billingKtx.getPurchases(BillingClient.ProductType.SUBS) } returns
                listOf(acknowledgedPurchase, unacknowledgedPurchase)
            coEvery { billingKtx.getPurchases(BillingClient.ProductType.INAPP) } returns emptyList()
            coEvery { billingKtx.acknowledge(any()) } just Runs

            // When
            store.acknowledge()
                .test()
                .await()
                .assertComplete()

            // Then - only unacknowledged purchase should be acknowledged
            coVerify(exactly = 1) { billingKtx.acknowledge(any()) }
        }

        @Test
        @DisplayName("should acknowledge purchases from both subscriptions and in-app")
        fun acknowledgeBothTypes() = runBlocking {
            // Given
            val subPurchase = createMockPurchase("sub", "order_sub", "token_sub", isAcknowledged = false)
            val productPurchase = createMockPurchase("coins", "order_product", "token_product", isAcknowledged = false)

            coEvery { billingKtx.getPurchases(BillingClient.ProductType.SUBS) } returns listOf(subPurchase)
            coEvery { billingKtx.getPurchases(BillingClient.ProductType.INAPP) } returns listOf(productPurchase)
            coEvery { billingKtx.acknowledge(any()) } just Runs

            // When
            store.acknowledge()
                .test()
                .await()
                .assertComplete()

            // Then - both should be acknowledged
            coVerify(exactly = 2) { billingKtx.acknowledge(any()) }
        }

        @Test
        @DisplayName("should complete when all purchases already acknowledged")
        fun completeWhenAllAcknowledged() = runBlocking {
            // Given
            val purchase = createMockPurchase("sub", "order", "token", isAcknowledged = true)

            coEvery { billingKtx.getPurchases(BillingClient.ProductType.SUBS) } returns listOf(purchase)
            coEvery { billingKtx.getPurchases(BillingClient.ProductType.INAPP) } returns emptyList()

            // When/Then
            store.acknowledge()
                .test()
                .await()
                .assertComplete()

            coVerify(exactly = 0) { billingKtx.acknowledge(any()) }
        }
    }

    @Nested
    @DisplayName("fetchHistory")
    inner class FetchHistory {

        @Test
        @DisplayName("should fetch purchases for both subscriptions and in-app products")
        fun fetchBothPurchases() = runBlocking {
            // Given
            coEvery { billingKtx.getPurchases(BillingClient.ProductType.SUBS) } returns emptyList()
            coEvery { billingKtx.getPurchases(BillingClient.ProductType.INAPP) } returns emptyList()

            // When
            store.fetchHistory()
                .test()
                .await()
                .assertComplete()

            // Then
            coVerify { billingKtx.getPurchases(BillingClient.ProductType.SUBS) }
            coVerify { billingKtx.getPurchases(BillingClient.ProductType.INAPP) }
        }

        @Test
        @DisplayName("should propagate error from subscription fetch")
        fun propagateSubscriptionError() {
            // Given
            val error = RuntimeException("Fetch failed")
            coEvery { billingKtx.getPurchases(BillingClient.ProductType.SUBS) } throws error

            // When/Then
            store.fetchHistory()
                .test()
                .await()
                .assertError(error)
        }
    }

    @Nested
    @DisplayName("getProductsDetails")
    inner class GetProductsDetails {

        @Test
        @DisplayName("should fetch product details for subscription type")
        fun fetchSubscriptionDetails() = runBlocking {
            // Given
            val productDetails = createMockProductDetails("premium_monthly", BillingClient.ProductType.SUBS)
            val requests = mapOf(BillingClient.ProductType.SUBS to listOf("premium_monthly"))

            coEvery { billingKtx.getProductDetails(any()) } returns listOf(productDetails)

            // When
            val result = store.getProductsDetails(requests)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result.first().productId).isEqualTo("premium_monthly")
        }

        @Test
        @DisplayName("should return empty list for empty requests")
        fun returnEmptyForEmptyRequests() = runBlocking {
            // When
            val result = store.getProductsDetails(emptyMap())

            // Then
            assertThat(result).isEmpty()
            coVerify(exactly = 0) { billingKtx.getProductDetails(any()) }
        }
    }

    // Helper functions

    private fun createMockPurchase(
        productId: String,
        orderId: String,
        purchaseToken: String,
        isAcknowledged: Boolean = false
    ): Purchase = mockk {
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
