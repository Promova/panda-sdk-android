package com.appsci.panda.sdk.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.appsci.panda.sdk.domain.utils.DeviceManager
import com.appsci.panda.sdk.domain.utils.Preferences
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import timber.log.Timber
import java.io.IOException
import java.util.*

class DeviceManagerImpl constructor(
        private val context: Context,
        private val preferences: Preferences
) : DeviceManager {

    override fun getAppName(): String =
            context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(context.packageName, 0)
            ).toString()

    override fun getAppVersionName(): String =
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    override fun getAppVersionCode(): Int =
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode

    override fun getDeviceFamily(): String = Build.MANUFACTURER

    override fun getDeviceModel(): String = Build.MODEL

    override fun getOsDetails(): String {
        return "${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT}) ${Build.VERSION.CODENAME}"
    }

    override fun getOsVersionName(): String {
        return Build.VERSION.RELEASE
    }

    override fun getFcmToken(): String? =
            try {
                Tasks.await(FirebaseMessaging.getInstance().token)
            } catch (e: Exception) {
                null
            }

    @SuppressLint("HardwareIds")
    override fun getHardwareId(): String =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    override fun hardwareUUID(): String =
            UUID.nameUUIDFromBytes(getHardwareId().toByteArray()).toString()

    override fun getAdvertisingId(): String? {
        var adInfo: AdvertisingIdClient.Info? = null
        try {
            adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context)
        } catch (e: IOException) {
            Timber.e(e, "Unrecoverable error connecting to Google Play services (e.g.), the old version of the service doesn't support getting AdvertisingId")
        } catch (e: GooglePlayServicesNotAvailableException) {
            Timber.e(e, "Google Play services is not available entirely!")
        } catch (e: GooglePlayServicesRepairableException) {
            Timber.e(e, "Thrown when Google Play Services is not installed, up-to-date, or enabled.")
        } catch (e: Exception) {
            Timber.e(e)
        }

        return adInfo?.id
    }

    override fun getTimeZoneId(): String = TimeZone.getDefault().id

    override fun getLocale(): Locale = Locale.getDefault()

    override fun installTime(): Long {
        return context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    }

    override fun updateTime(): Long {
        return context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    }

    override val startVersion: String
        get() = preferences.startVersion ?: getAppVersionName()

}
