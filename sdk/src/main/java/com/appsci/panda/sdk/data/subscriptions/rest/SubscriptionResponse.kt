package com.appsci.panda.sdk.data.subscriptions.rest

import com.google.gson.annotations.SerializedName

data class SubscriptionResponse(
        @SerializedName("active")
        val active: Boolean
)
