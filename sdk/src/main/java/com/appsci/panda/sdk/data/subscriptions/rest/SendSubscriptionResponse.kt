package com.appsci.panda.sdk.data.subscriptions.rest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendSubscriptionResponse(
    @SerialName("active")
    val active: Boolean
)
