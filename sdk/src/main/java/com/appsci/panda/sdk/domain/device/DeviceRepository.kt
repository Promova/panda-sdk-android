package com.appsci.panda.sdk.domain.device

interface DeviceRepository {

    val pandaUserId: String?

    suspend fun authorize(): Device

    suspend fun clearAdvId()

    suspend fun ensureAuthorized()

    suspend fun getAuthState(): AuthState

    suspend fun deleteDevice()

    suspend fun clearLocalData()
}
