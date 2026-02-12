package com.appsci.panda.sdk.data.subscriptions.rest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductRequest(
    @SerialName("product_id")
    val productId: String,
    @SerialName("order_id")
    val orderId: String,
    @SerialName("purchase_token")
    val purchaseToken: String
)
