package com.appsci.panda.sdk.data.network

import com.appsci.panda.sdk.data.subscriptions.rest.ScreenDataResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

interface ScreenApi {

    @GET("/android/{screen_id}/data.json")
    suspend fun getSubscriptionScreen(
        @Path("screen_id") screenId: String,
    ): ScreenDataResponse

    @GET
    suspend fun getScreenHtml(
        @Url url: String,
    ): String

}
