package com.appsci.panda.sdk.data.subscriptions.rest

import com.google.gson.annotations.SerializedName

data class ScreenDataResponse(
    @SerializedName("screen_html")
    val htmlUrl: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("id")
    val id: String
)

data class ScreenData(
    val screenHtml: String,
    val name: String,
    val id: String
)
