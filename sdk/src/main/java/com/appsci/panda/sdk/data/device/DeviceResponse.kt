package com.appsci.panda.sdk.data.device

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceResponse(
    @SerialName("id")
    val id: String
)
