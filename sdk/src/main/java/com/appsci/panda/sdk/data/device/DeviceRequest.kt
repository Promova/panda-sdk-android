package com.appsci.panda.sdk.data.device

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceRequest(
    @SerialName("country")
    val country: String,
    @SerialName("device_model")
    val deviceModel: String,
    @SerialName("app_version")
    val appVersion: String,
    @SerialName("start_app_version")
    val startAppVersion: String,
    @SerialName("timezone")
    val timeZone: String,
    @SerialName("os_version")
    val osVersion: String,
    @SerialName("idfa")
    val idfa: String? = null,
    @SerialName("device_family")
    val deviceFamily: String,
    @SerialName("language")
    val language: String,
    @SerialName("locale")
    val locale: String,
    @SerialName("platform")
    val platform: String,
    @SerialName("push_notifications_token")
    val pushToken: String? = null,
    @SerialName("custom_user_id")
    val customUserId: String? = null,
    @SerialName("appsflyer_id")
    val appsflyerId: String? = null,
    @SerialName("time_zone")
    val idfv: String? = null,
    @SerialName("fbc")
    val fbc: String? = null,
    @SerialName("fbp")
    val fbp: String? = null,
    @SerialName("email")
    val email: String? = null,
    @SerialName("facebook_login_id")
    val facebookLoginId: String? = null,
    @SerialName("first_name")
    val firstName: String? = null,
    @SerialName("last_name")
    val lastName: String? = null,
    @SerialName("full_name")
    val fullName: String? = null,
    @SerialName("gender")
    val gender: Int? = null,
    @SerialName("phone")
    val phone: String? = null,
    @SerialName("properties")
    val properties: Map<String, String>?,
)
