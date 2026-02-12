package com.appsci.panda.sdk.ui

import com.android.billingclient.api.BillingClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class ProductPricingRequest(
    @SerialName("id")
    val id: String,
    @SerialName("type")
    val type: String,
)

interface ProductDetails

@Serializable
data class SubscriptionDetails(
    @SerialName("productId")
    val productId: String,
    @SerialName("type")
    val type: String,
    @SerialName("pricingPhases")
    val pricingPhases: List<PricingPhase>,
) : ProductDetails

@Serializable
data class InappDetails(
    @SerialName("productId")
    val productId: String,
    @SerialName("type")
    val type: String,
    @SerialName("oneTimePurchaseOfferDetail")
    val oneTimePurchaseOfferDetail: OneTimePurchaseOfferDetails,
) : ProductDetails

@Serializable
data class PricingPhase(
    @SerialName("priceAmountMicros")
    val priceAmountMicros: Long,
    @SerialName("priceCurrencyCode")
    val priceCurrencyCode: String,
    @SerialName("formattedPrice")
    val formattedPrice: String,
    @SerialName("billingPeriod")
    val billingPeriod: String,
    @SerialName("recurrenceMode")
    val recurrenceMode: Int,
    @SerialName("billingCycleCount")
    val billingCycleCount: Int,
)

@Serializable
data class OneTimePurchaseOfferDetails(
    @SerialName("priceAmountMicros")
    val priceAmountMicros: Long,
    @SerialName("priceCurrencyCode")
    val priceCurrencyCode: String,
    @SerialName("formattedPrice")
    val formattedPrice: String,
)

fun List<com.android.billingclient.api.ProductDetails>.toModels(): List<ProductDetails> {
    return this.mapNotNull { productDetails ->
        when (productDetails.productType) {
            BillingClient.ProductType.INAPP -> {
                val oneTimePurchaseOfferDetails = productDetails.oneTimePurchaseOfferDetails
                    ?: return@mapNotNull null
                InappDetails(
                    productId = productDetails.productId,
                    type = productDetails.productType,
                    oneTimePurchaseOfferDetail = OneTimePurchaseOfferDetails(
                        priceAmountMicros = oneTimePurchaseOfferDetails.priceAmountMicros,
                        priceCurrencyCode = oneTimePurchaseOfferDetails.priceCurrencyCode,
                        formattedPrice = oneTimePurchaseOfferDetails.formattedPrice,
                    )
                )
            }

            BillingClient.ProductType.SUBS -> {
                val subscriptionOfferDetails = productDetails.subscriptionOfferDetails?.first()
                    ?: return@mapNotNull null
                SubscriptionDetails(
                    productId = productDetails.productId,
                    type = productDetails.productType,
                    pricingPhases = subscriptionOfferDetails.pricingPhases.pricingPhaseList.map {
                        PricingPhase(
                            priceAmountMicros = it.priceAmountMicros,
                            priceCurrencyCode = it.priceCurrencyCode,
                            formattedPrice = it.formattedPrice,
                            recurrenceMode = it.recurrenceMode,
                            billingPeriod = it.billingPeriod,
                            billingCycleCount = it.billingCycleCount,
                        )
                    }
                )
            }

            else -> null
        }
    }
}

/**
 * Encode list of ProductDetails to JSON string, preserving the same format as Gson.
 * Each element is serialized based on its concrete type without a type discriminator.
 */
fun List<ProductDetails>.encodeToJson(): String {
    val json = Json
    val elements: List<JsonElement> = map { detail ->
        when (detail) {
            is SubscriptionDetails -> json.encodeToJsonElement(detail)
            is InappDetails -> json.encodeToJsonElement(detail)
            else -> error("Unknown ProductDetails type")
        }
    }
    return json.encodeToString(elements)
}
