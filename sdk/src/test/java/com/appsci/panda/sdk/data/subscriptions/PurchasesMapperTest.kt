package com.appsci.panda.sdk.data.subscriptions

import com.android.billingclient.api.Purchase
import com.appsci.panda.sdk.data.subscriptions.local.PurchaseEntity
import com.appsci.panda.sdk.data.subscriptions.local.TYPE_PRODUCT
import com.appsci.panda.sdk.data.subscriptions.local.TYPE_SUBSCRIPTION
import com.appsci.panda.sdk.domain.subscriptions.SkuType
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for PurchasesMapper - critical for billing library migration.
 *
 * These tests verify the mapping logic between:
 * - Google Billing Library Purchase -> PurchaseEntity (local DB)
 * - PurchaseEntity -> Domain Purchase
 *
 * Now using Billing v8 with `purchase.products` instead of deprecated `purchase.skus`.
 */
@DisplayName("PurchasesMapper")
class PurchasesMapperTest {

    private lateinit var mapper: PurchasesMapperImpl

    @BeforeEach
    fun setUp() {
        mapper = PurchasesMapperImpl()
    }

    @Nested
    @DisplayName("mapFromBillingPurchases")
    inner class MapFromBillingPurchases {

        @Test
        @DisplayName("should map subscription purchase correctly")
        fun mapSubscriptionPurchase() {
            // Given
            val billingPurchase = createMockBillingPurchase(
                productIds = listOf("premium_monthly"),
                orderId = "GPA.1234-5678-9012",
                purchaseToken = "token_abc123"
            )

            // When
            val result = mapper.mapFromBillingPurchases(
                purchases = listOf(billingPurchase),
                type = TYPE_SUBSCRIPTION
            )

            // Then
            assertThat(result).hasSize(1)
            with(result.first()) {
                assertThat(productId).isEqualTo("premium_monthly")
                assertThat(orderId).isEqualTo("GPA.1234-5678-9012")
                assertThat(purchaseToken).isEqualTo("token_abc123")
                assertThat(purchaseType).isEqualTo(TYPE_SUBSCRIPTION)
                assertThat(synced).isFalse
            }
        }

        @Test
        @DisplayName("should map in-app product purchase correctly")
        fun mapInAppPurchase() {
            // Given
            val billingPurchase = createMockBillingPurchase(
                productIds = listOf("coins_100"),
                orderId = "GPA.9999-8888-7777",
                purchaseToken = "token_xyz789"
            )

            // When
            val result = mapper.mapFromBillingPurchases(
                purchases = listOf(billingPurchase),
                type = TYPE_PRODUCT
            )

            // Then
            assertThat(result).hasSize(1)
            with(result.first()) {
                assertThat(productId).isEqualTo("coins_100")
                assertThat(orderId).isEqualTo("GPA.9999-8888-7777")
                assertThat(purchaseToken).isEqualTo("token_xyz789")
                assertThat(purchaseType).isEqualTo(TYPE_PRODUCT)
                assertThat(synced).isFalse
            }
        }

        @Test
        @DisplayName("should map multiple purchases")
        fun mapMultiplePurchases() {
            // Given
            val purchases = listOf(
                createMockBillingPurchase(
                    productIds = listOf("product_1"),
                    orderId = "order_1",
                    purchaseToken = "token_1"
                ),
                createMockBillingPurchase(
                    productIds = listOf("product_2"),
                    orderId = "order_2",
                    purchaseToken = "token_2"
                ),
                createMockBillingPurchase(
                    productIds = listOf("product_3"),
                    orderId = "order_3",
                    purchaseToken = "token_3"
                )
            )

            // When
            val result = mapper.mapFromBillingPurchases(purchases, TYPE_SUBSCRIPTION)

            // Then
            assertThat(result).hasSize(3)
            assertThat(result.map { it.productId }).containsExactly("product_1", "product_2", "product_3")
        }

        @Test
        @DisplayName("should return empty list for empty input")
        fun mapEmptyList() {
            // When
            val result = mapper.mapFromBillingPurchases(emptyList(), TYPE_SUBSCRIPTION)

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("should use first product ID when multiple products in single purchase")
        fun mapPurchaseWithMultipleProducts() {
            // Given - This is important for v8 migration where products is a List
            val billingPurchase = createMockBillingPurchase(
                productIds = listOf("primary_product", "secondary_product"),
                orderId = "order_multi",
                purchaseToken = "token_multi"
            )

            // When
            val result = mapper.mapFromBillingPurchases(
                purchases = listOf(billingPurchase),
                type = TYPE_SUBSCRIPTION
            )

            // Then
            assertThat(result).hasSize(1)
            assertThat(result.first().productId).isEqualTo("primary_product")
        }

        @Test
        @DisplayName("should handle empty orderId")
        fun mapPurchaseWithEmptyOrderId() {
            // Given - orderId can be empty in edge cases
            val billingPurchase = createMockBillingPurchase(
                productIds = listOf("product"),
                orderId = "",
                purchaseToken = "token"
            )

            // When
            val result = mapper.mapFromBillingPurchases(
                purchases = listOf(billingPurchase),
                type = TYPE_SUBSCRIPTION
            )

            // Then
            assertThat(result).hasSize(1)
            assertThat(result.first().orderId).isEmpty()
        }

        @Test
        @DisplayName("should handle null orderId (Billing v8)")
        fun mapPurchaseWithNullOrderId() {
            // Given - orderId can be null in Billing v8
            val billingPurchase = createMockBillingPurchase(
                productIds = listOf("product"),
                orderId = null,
                purchaseToken = "token"
            )

            // When
            val result = mapper.mapFromBillingPurchases(
                purchases = listOf(billingPurchase),
                type = TYPE_SUBSCRIPTION
            )

            // Then
            assertThat(result).hasSize(1)
            assertThat(result.first().orderId).isEmpty()
        }

        @Test
        @DisplayName("should handle empty products list")
        fun mapPurchaseWithEmptyProductsList() {
            // Given - edge case for empty products list
            val billingPurchase = createMockBillingPurchase(
                productIds = emptyList(),
                orderId = "order",
                purchaseToken = "token"
            )

            // When
            val result = mapper.mapFromBillingPurchases(
                purchases = listOf(billingPurchase),
                type = TYPE_SUBSCRIPTION
            )

            // Then
            assertThat(result).hasSize(1)
            assertThat(result.first().productId).isEmpty()
        }
    }

    @Nested
    @DisplayName("mapToDomain")
    inner class MapToDomain {

        @Test
        @DisplayName("should map subscription entity to domain Purchase")
        fun mapSubscriptionEntity() {
            // Given
            val entity = PurchaseEntity(
                productId = "premium_yearly",
                orderId = "GPA.1111-2222-3333",
                purchaseToken = "domain_token",
                purchaseType = TYPE_SUBSCRIPTION,
                synced = true
            )

            // When
            val result = mapper.mapToDomain(entity)

            // Then
            assertThat(result.id).isEqualTo("premium_yearly")
            assertThat(result.orderId).isEqualTo("GPA.1111-2222-3333")
            assertThat(result.token).isEqualTo("domain_token")
            assertThat(result.type).isEqualTo(SkuType.SUBSCRIPTION)
        }

        @Test
        @DisplayName("should map product entity to domain Purchase")
        fun mapProductEntity() {
            // Given
            val entity = PurchaseEntity(
                productId = "coins_500",
                orderId = "GPA.4444-5555-6666",
                purchaseToken = "product_token",
                purchaseType = TYPE_PRODUCT,
                synced = false
            )

            // When
            val result = mapper.mapToDomain(entity)

            // Then
            assertThat(result.id).isEqualTo("coins_500")
            assertThat(result.orderId).isEqualTo("GPA.4444-5555-6666")
            assertThat(result.token).isEqualTo("product_token")
            assertThat(result.type).isEqualTo(SkuType.INAPP)
        }

        @Test
        @DisplayName("should throw exception for unknown purchase type")
        fun mapUnknownType() {
            // Given
            val entity = PurchaseEntity(
                productId = "unknown",
                orderId = "order",
                purchaseToken = "token",
                purchaseType = 999, // Unknown type
                synced = false
            )

            // Then
            assertThatThrownBy { mapper.mapToDomain(entity) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Unknown purchase type 999")
        }
    }

    /**
     * Helper function to create a mock Billing Purchase.
     *
     * Using Billing v8 API with `purchase.products` property.
     * Note: orderId can be null in v8.
     */
    private fun createMockBillingPurchase(
        productIds: List<String>,
        orderId: String?,
        purchaseToken: String,
        isAcknowledged: Boolean = false
    ): Purchase {
        val mock = mockk<Purchase>(relaxed = true)
        every { mock.products } returns ArrayList(productIds)
        every { mock.orderId } returns orderId
        every { mock.purchaseToken } returns purchaseToken
        every { mock.isAcknowledged } returns isAcknowledged
        return mock
    }
}
