package com.appsci.panda.sdk.data.subscriptions.rest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScreenDataResponse(
    @SerialName("screen_html")
    val htmlUrl: String,
    @SerialName("name")
    val name: String,
    @SerialName("id")
    val id: String
)

data class ScreenData(
    val screenHtml: String,
    val name: String,
    val id: String
)
