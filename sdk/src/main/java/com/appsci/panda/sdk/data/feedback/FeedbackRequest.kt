package com.appsci.panda.sdk.data.feedback

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeedbackRequest(
    @SerialName("user_id")
    val userId: String,
    @SerialName("screen_id")
    val screenId: String,
    @SerialName("answer")
    val answer: String,
)
