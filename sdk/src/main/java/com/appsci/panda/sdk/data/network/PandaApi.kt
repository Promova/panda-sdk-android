package com.appsci.panda.sdk.data.network

import com.appsci.panda.sdk.data.device.DeviceRequest
import com.appsci.panda.sdk.data.device.DeviceResponse
import com.appsci.panda.sdk.data.feedback.FeedbackRequest
import com.appsci.panda.sdk.data.subscriptions.rest.*
import retrofit2.http.*

interface PandaApi {

    @POST("/v1/users")
    suspend fun registerDevice(@Body deviceRequest: DeviceRequest): DeviceResponse

    @PUT("/v1/users/{user_id}")
    suspend fun updateDevice(
        @Body deviceRequest: DeviceRequest,
        @Path("user_id") userId: String,
    ): DeviceResponse

    @DELETE("/v1/devices")
    suspend fun deleteDevice()

    @GET("/v1/subscription-status/{user_id}")
    suspend fun getSubscriptionStatus(
        @Path("user_id") userId: String,
    ): SubscriptionStateResponse

    @POST("/v1/android/products/{user_id}")
    suspend fun sendProduct(
        @Body request: ProductRequest,
        @Path("user_id") userId: String,
    ): SendSubscriptionResponse

    @POST("/v1/android/subscriptions/{user_id}")
    suspend fun sendSubscription(
        @Body request: SubscriptionRequest,
        @Path("user_id") userId: String,
    ): SendSubscriptionResponse

    @POST("/v1/feedback/answers")
    suspend fun sendFeedback(
        @Body request: FeedbackRequest,
    )

}
