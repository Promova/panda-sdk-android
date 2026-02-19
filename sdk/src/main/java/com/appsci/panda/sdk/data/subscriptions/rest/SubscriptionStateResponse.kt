package com.appsci.panda.sdk.data.subscriptions.rest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionStateResponse(
    @SerialName("state")
    val state: String,
    @SerialName("subscriptions")
    val subscriptions: SubscriptionsResponse
)

@Serializable
data class SubscriptionsResponse(
    @SerialName("android")
    val android: List<SubscriptionResponse>? = null,
    @SerialName("ios")
    val ios: List<SubscriptionResponse>? = null,
    @SerialName("web")
    val web: List<SubscriptionResponse>? = null
)

@Serializable
data class SubscriptionResponse(
    @SerialName("order_id")
    val orderId: String,
    @SerialName("subscription_id")
    val subscriptionId: String,
    @SerialName("is_trial_period")
    val isTrial: Boolean,
    @SerialName("product_id")
    val productId: String,
    @SerialName("state")
    val state: String,
    @SerialName("is_intro_offer")
    val isIntroOffer: Boolean? = null,
    @SerialName("payment_type")
    val paymentType: String? = null,
)
