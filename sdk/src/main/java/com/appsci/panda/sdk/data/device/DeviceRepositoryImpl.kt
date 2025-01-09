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
import com.appsci.panda.sdk.domain.utils.rx.shareSingle
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
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

    /**
     * auth observable that shares result for all subscribers
     */
    private val authSharedSingle = createAuthObservable().shareSingle()

    /**
     * auth observable that shares result for all subscribers
     */
    private val ensureAuthorizedSingle = createEnsureAuthObservable().shareSingle()

    override val pandaUserId: String?
        get() = preferences.pandaUserId

    /**
     *  perform device authorization, or update device if changed, or returns existing device from local storage
     */
    override fun authorize(): Single<Device> = authSharedSingle

    /**
     *  perform device authorization or returns existing device from local storage
     */
    override fun ensureAuthorized(): Completable =
        ensureAuthorizedSingle.ignoreElement()

    override fun getAuthState(): Single<AuthState> {
        return deviceDao.selectDevice().toSingle()
            .map<AuthState> { AuthState.Authorized(deviceMapper.mapToDomain(it)) }
            .onErrorReturnItem(AuthState.NotAuthorized)
    }

    override fun deleteDevice(): Completable {
        return pandaApi.deleteDevice()
            .andThen(clearLocalData())
    }

    override fun clearLocalData(): Completable = Completable.fromAction {
        database.clearAllTables()
        preferences.clear()
        localPropertiesDataSource.clear()
    }

    private fun createAuthObservable(): Single<Device> {
        return Single.defer {
            deviceDao.selectDevice()
                .flatMapSingleElement { updateDevice(it) }
                .switchIfEmpty(
                    registerDevice()
                        .flatMap {
                            //update right after register, if need
                            deviceDao.selectDevice().toSingle()
                                .flatMap { updateDevice(it) }
                        }
                )
        }
    }

    private fun createEnsureAuthObservable(): Single<Device> {
        return Single.defer {
            deviceDao.selectDevice()
                .map { deviceMapper.mapToDomain(it) }
                .switchIfEmpty(authSharedSingle)
        }
    }

    private fun registerDevice(): Single<Device> {
        return Single.defer {
            val authData = authorizationDataBuilder.createAuthData()
            Timber.d("registerDevice $authData")
            val registerRequest = deviceMapper.mapRegisterRequest(authData)
            return@defer pandaApi.registerDevice(registerRequest)
                .map { deviceMapper.mapToLocal(it, registerRequest) }
                .doOnSuccess {
                    preferences.pandaUserId = it.id
                    deviceDao.putDevice(it)
                }
                .doOnError { Timber.e(it) }
                .map { deviceMapper.mapToDomain(it) }
        }
    }

    private fun updateDevice(deviceEntity: DeviceEntity): Single<Device> {
        Timber.d("updateDevice $deviceEntity")
        return Single.defer {
            val authData = authorizationDataBuilder.createAuthData()
            if (authDataValidator.isDeviceValid(deviceEntity, authData)) {
                Timber.d("updateDevice skipped")
                return@defer Single.just(deviceMapper.mapToDomain(deviceEntity))
            } else {
                val updateRequest = deviceMapper.mapUpdateRequest(authData)
                return@defer pandaApi.updateDevice(updateRequest, deviceEntity.id)
                    .map { deviceMapper.mapToLocal(it, updateRequest) }
                    .doOnSuccess {
                        preferences.pandaUserId = it.id
                        deviceDao.putDevice(it)
                    }
                    .map { deviceMapper.mapToDomain(it) }
            }
        }.onErrorReturn { deviceMapper.mapToDomain(deviceEntity) }
    }

    override fun clearAdvId(): Completable {
        return Maybe.defer {
            deviceDao.selectDevice()
                .flatMapSingleElement { deviceEntity ->
                    val authData = authorizationDataBuilder.createAuthData()
                        .copy(idfa = "")
                    val updateRequest = deviceMapper.mapUpdateRequest(authData)
                    return@flatMapSingleElement pandaApi.updateDevice(updateRequest, deviceEntity.id)
                        .map { deviceMapper.mapToLocal(it, updateRequest) }
                        .doOnSuccess {
                            deviceDao.putDevice(it)
                        }
                }
        }.ignoreElement()
    }
}
