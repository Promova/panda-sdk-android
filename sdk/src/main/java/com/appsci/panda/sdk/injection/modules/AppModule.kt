package com.appsci.panda.sdk.injection.modules

import android.content.Context
import android.content.res.Resources
import com.appsci.panda.sdk.IPanda
import com.appsci.panda.sdk.PandaImpl
import com.appsci.panda.sdk.data.DeviceManagerImpl
import com.appsci.panda.sdk.data.LocalPropertiesDataSourceImpl
import com.appsci.panda.sdk.data.PreferencesImpl
import com.appsci.panda.sdk.data.StopNetwork
import com.appsci.panda.sdk.domain.device.DeviceRepository
import com.appsci.panda.sdk.domain.feedback.FeedbackRepository
import com.appsci.panda.sdk.domain.subscriptions.SubscriptionsRepository
import com.appsci.panda.sdk.domain.utils.DeviceManager
import com.appsci.panda.sdk.domain.utils.LocalPropertiesDataSource
import com.appsci.panda.sdk.domain.utils.Preferences
import dagger.Lazy
import dagger.Module
import dagger.Provides
import org.threeten.bp.Clock
import javax.inject.Singleton

@Module
class AppModule(private val context: Context) {

    @Provides
    fun providePanda(
        deviceRepository: Lazy<DeviceRepository>,
        subscriptionsRepository: Lazy<SubscriptionsRepository>,
        preferences: Lazy<Preferences>,
        deviceManager: Lazy<DeviceManager>,
        stopNetwork: Lazy<StopNetwork>,
        localPropertiesDataSource: Lazy<LocalPropertiesDataSource>,
        feedbackRepository: Lazy<FeedbackRepository>,
    ): IPanda {
        return PandaImpl(
            preferencesLazy = preferences,
            deviceManagerLazy = deviceManager,
            deviceRepositoryLazy = deviceRepository,
            subscriptionsRepositoryLazy = subscriptionsRepository,
            stopNetworkInternalLazy = stopNetwork,
            propertiesDataSourceLazy = localPropertiesDataSource,
            feedbackRepositoryLazy = feedbackRepository,
        )
    }

    @Provides
    fun provideAppContext(): Context = context

    @Provides
    fun provideResources(context: Context): Resources = context.resources

    @Provides
    fun provideDeviceManager(
        appContext: Context,
        preferences: Preferences,
    ): DeviceManager {
        return DeviceManagerImpl(appContext, preferences)
    }

    @Provides
    @Singleton
    fun providePreferences(context: Context): Preferences {
        return PreferencesImpl(context)
    }

    @Provides
    @Singleton
    fun providePropertiesDataSource(context: Context): LocalPropertiesDataSource {
        return LocalPropertiesDataSourceImpl(
            context.getSharedPreferences("PropertiesPreferences", Context.MODE_PRIVATE)
        )
    }

    @Provides
    @Singleton
    fun provideClock(): Clock =
        Clock.systemDefaultZone()

}
