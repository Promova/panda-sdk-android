package com.appsci.panda.sdk.data.device

import com.appsci.panda.sdk.data.db.PandaDatabase
import com.appsci.panda.sdk.data.device.utils.AuthDataValidator
import com.appsci.panda.sdk.data.device.utils.AuthorizationDataBuilder
import com.appsci.panda.sdk.data.network.PandaApi
import com.appsci.panda.sdk.domain.device.AuthState
import com.appsci.panda.sdk.domain.device.Device
import com.appsci.panda.sdk.domain.device.DeviceRepository
import com.appsci.panda.sdk.domain.utils.LocalPropertiesDataSource
import com.appsci.panda.sdk.domain.utils.Preferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject

class DeviceRepositoryImpl @Inject constructor(
    private val database: PandaDatabase,
    private val pandaApi: PandaApi,
    private val authorizationDataBuilder: AuthorizationDataBuilder,
    private val authDataValidator: AuthDataValidator,
    private val deviceMapper: DeviceMapper,
    private val preferences: Preferences,
    private val localPropertiesDataSource: LocalPropertiesDataSource,
) : DeviceRepository {

    private val deviceDao: DeviceDao = database.getDeviceDao()

    private val authMutex = Mutex()
    private val ensureAuthMutex = Mutex()

    override val pandaUserId: String?
        get() = preferences.pandaUserId

    /**
     *  perform device authorization, or update device if changed, or returns existing device from local storage
     */
    override suspend fun authorize(): Device = authMutex.withLock {
        val existing = deviceDao.selectDevice()
        if (existing != null) {
            updateDevice(existing)
        } else {
            val registered = registerDevice()
            // update right after register, if needed
            val justRegistered = deviceDao.selectDevice()
            if (justRegistered != null) {
                updateDevice(justRegistered)
            } else {
                registered
            }
        }
    }

    /**
     *  perform device authorization or returns existing device from local storage
     */
    override suspend fun ensureAuthorized() {
        ensureAuthMutex.withLock {
            val existing = deviceDao.selectDevice()
            if (existing != null) {
                deviceMapper.mapToDomain(existing)
            } else {
                authorize()
            }
        }
    }

    override suspend fun getAuthState(): AuthState {
        return try {
            val entity = deviceDao.selectDevice()
                ?: return AuthState.NotAuthorized
            AuthState.Authorized(deviceMapper.mapToDomain(entity))
        } catch (_: Exception) {
            AuthState.NotAuthorized
        }
    }

    override suspend fun deleteDevice() {
        pandaApi.deleteDevice()
        clearLocalData()
    }

    override suspend fun clearLocalData() {
        database.clearAllTables()
        preferences.clear()
        localPropertiesDataSource.clear()
    }

    private suspend fun registerDevice(): Device {
        val authData = authorizationDataBuilder.createAuthData()
        Timber.d("registerDevice $authData")
        val registerRequest = deviceMapper.mapRegisterRequest(authData)
        val response = pandaApi.registerDevice(registerRequest)
        val entity = deviceMapper.mapToLocal(response, registerRequest)
        preferences.pandaUserId = entity.id
        deviceDao.putDevice(entity)
        return deviceMapper.mapToDomain(entity)
    }

    private suspend fun updateDevice(deviceEntity: DeviceEntity): Device {
        Timber.d("updateDevice $deviceEntity")
        return try {
            val authData = authorizationDataBuilder.createAuthData()
            if (authDataValidator.isDeviceValid(deviceEntity, authData)) {
                Timber.d("updateDevice skipped")
                return deviceMapper.mapToDomain(deviceEntity)
            }
            val updateRequest = deviceMapper.mapUpdateRequest(authData)
            val response = pandaApi.updateDevice(updateRequest, deviceEntity.id)
            val entity = deviceMapper.mapToLocal(response, updateRequest)
            preferences.pandaUserId = entity.id
            deviceDao.putDevice(entity)
            deviceMapper.mapToDomain(entity)
        } catch (e: Exception) {
            deviceMapper.mapToDomain(deviceEntity)
        }
    }

    override suspend fun clearAdvId() {
        val deviceEntity = deviceDao.selectDevice() ?: return
        val authData = authorizationDataBuilder.createAuthData()
            .copy(idfa = "")
        val updateRequest = deviceMapper.mapUpdateRequest(authData)
        val response = pandaApi.updateDevice(updateRequest, deviceEntity.id)
        val entity = deviceMapper.mapToLocal(response, updateRequest)
        deviceDao.putDevice(entity)
    }
}
