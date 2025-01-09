package com.appsci.panda.sdk.injection.modules

import android.content.Context
import com.appsci.panda.sdk.data.db.PandaDatabase
import com.appsci.panda.sdk.data.device.DeviceDao
import com.appsci.panda.sdk.data.network.PandaApi
import com.appsci.panda.sdk.data.network.ScreenApi
import com.appsci.panda.sdk.data.subscriptions.PurchasesMapper
import com.appsci.panda.sdk.data.subscriptions.PurchasesMapperImpl
import com.appsci.panda.sdk.data.subscriptions.SubscriptionsRepositoryImpl
import com.appsci.panda.sdk.data.subscriptions.google.BillingValidatorImpl
import com.appsci.panda.sdk.data.subscriptions.google.PurchasesGoogleStore
import com.appsci.panda.sdk.data.subscriptions.google.PurchasesGoogleStoreImpl
import com.appsci.panda.sdk.data.subscriptions.local.FileStoreImpl
import com.appsci.panda.sdk.data.subscriptions.local.PurchasesLocalStore
import com.appsci.panda.sdk.data.subscriptions.local.PurchasesLocalStoreImpl
import com.appsci.panda.sdk.data.subscriptions.rest.PurchasesRestStore
import com.appsci.panda.sdk.data.subscriptions.rest.PurchasesRestStoreImpl
import com.appsci.panda.sdk.domain.subscriptions.SubscriptionsRepository
import com.gen.rxbilling.client.RxBilling
import com.gen.rxbilling.client.RxBillingImpl
import com.gen.rxbilling.connection.BillingClientFactory
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class BillingModule(private val context: Context) {

    @Provides
    @Singleton
    fun provideRxBilling(): RxBilling = RxBillingImpl(BillingClientFactory(context))

    @Provides
    @Singleton
    fun provideSubscriptionsRepository(
        localStore: PurchasesLocalStore,
        googleStore: PurchasesGoogleStore,
        restStore: PurchasesRestStore,
        mapper: PurchasesMapper,
        deviceDao: DeviceDao,
    ): SubscriptionsRepository {
        return SubscriptionsRepositoryImpl(
            localStore,
            googleStore,
            restStore,
            mapper,
            BillingValidatorImpl(),
            deviceDao,
            FileStoreImpl(context),
        )
    }

    @Provides
    @Singleton
    fun providePurchasesLocalStore(appDatabase: PandaDatabase): PurchasesLocalStore {
        return PurchasesLocalStoreImpl(appDatabase.getPurchaseDao())
    }

    @Provides
    @Singleton
    fun providePurchasesRestStore(
        pandaApi: PandaApi,
        screenApi: ScreenApi,
    ): PurchasesRestStore {
        return PurchasesRestStoreImpl(pandaApi, screenApi)
    }

    @Provides
    @Singleton
    fun providePurchasesGoogleStore(
        rxBilling: RxBilling,
        mapper: PurchasesMapper,
    ): PurchasesGoogleStore {
        return PurchasesGoogleStoreImpl(rxBilling, mapper)
    }

    @Provides
    fun providePurchaseMapper(): PurchasesMapper {
        return PurchasesMapperImpl()
    }

}
