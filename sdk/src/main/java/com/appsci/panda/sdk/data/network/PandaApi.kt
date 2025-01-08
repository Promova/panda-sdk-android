package com.appsci.panda.sdk.data.network

import com.appsci.panda.sdk.data.device.DeviceRequest
import com.appsci.panda.sdk.data.device.DeviceResponse
import com.appsci.panda.sdk.data.feedback.FeedbackRequest
import com.appsci.panda.sdk.data.subscriptions.rest.*
import io.reactivex.Completable
import io.reactivex.Single
import retrofit2.http.*

interface PandaApi {

    @POST("/v1/users")
    fun registerDevice(@Body deviceRequest: DeviceRequest): Single<DeviceResponse>

    @PUT("/v1/users/{user_id}")
    fun updateDevice(
        @Body deviceRequest: DeviceRequest,
        @Path("user_id") userId: String,
    ): Single<DeviceResponse>

    @DELETE("/v1/devices")
    fun deleteDevice(): Completable

    @GET("/v1/subscription-status/{user_id}")
    fun getSubscriptionStatus(
        @Path("user_id") userId: String,
    ): Single<SubscriptionStateResponse>

    @POST("/v1/android/products/{user_id}")
    fun sendProduct(
        @Body request: ProductRequest,
        @Path("user_id") userId: String,
    ): Single<SendSubscriptionResponse>

    @POST("/v1/android/subscriptions/{user_id}")
    fun sendSubscription(
        @Body request: SubscriptionRequest,
        @Path("user_id") userId: String,
    ): Single<SendSubscriptionResponse>

    @POST("/v1/feedback/answers")
    suspend fun sendFeedback(
        @Body request: FeedbackRequest,
    )

}
