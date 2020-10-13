package com.appsci.panda.sdk.injection.modules

import android.content.Context
import com.appsci.panda.sdk.data.db.PandaDatabase
import com.appsci.panda.sdk.data.network.RestApi
import com.appsci.panda.sdk.data.subscriptions.PurchasesMapper
import com.appsci.panda.sdk.data.subscriptions.PurchasesMapperImpl
import com.appsci.panda.sdk.data.subscriptions.SubscriptionsRepositoryImpl
import com.appsci.panda.sdk.data.subscriptions.google.BillingValidatorImpl
import com.appsci.panda.sdk.data.subscriptions.google.PurchasesGoogleStore
import com.appsci.panda.sdk.data.subscriptions.google.PurchasesGoogleStoreImpl
import com.appsci.panda.sdk.data.subscriptions.local.PurchasesLocalStore
import com.appsci.panda.sdk.data.subscriptions.local.PurchasesLocalStoreImpl
import com.appsci.panda.sdk.data.subscriptions.rest.PurchasesRestStore
import com.appsci.panda.sdk.data.subscriptions.rest.PurchasesRestStoreImpl
import com.appsci.panda.sdk.domain.subscriptions.SubscriptionsRepository
import com.gen.rxbilling.client.RxBilling
import com.gen.rxbilling.client.RxBillingImpl
import com.gen.rxbilling.connection.BillingClientFactory
import com.gen.rxbilling.connection.BillingServiceFactory
import com.gen.rxbilling.flow.RxBillingFlow
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class BillingModule(private val context: Context) {

    @Provides
    fun provideRxBillingFlow(): RxBillingFlow = RxBillingFlow(context, BillingServiceFactory(context))

    @Provides
    @Singleton
    fun provideRxBilling(): RxBilling = RxBillingImpl(BillingClientFactory(context))

    @Provides
    @Singleton
    fun provideSubscriptionsRepository(
            localStore: PurchasesLocalStore,
            googleStore: PurchasesGoogleStore,
            restStore: PurchasesRestStore,
            mapper: PurchasesMapper
    ): SubscriptionsRepository {
        return SubscriptionsRepositoryImpl(
                localStore,
                googleStore,
                restStore,
                mapper,
                BillingValidatorImpl())
    }

    @Provides
    @Singleton
    fun providePurchasesLocalStore(appDatabase: PandaDatabase): PurchasesLocalStore {
        return PurchasesLocalStoreImpl(appDatabase.getPurchaseDao())
    }

    @Provides
    @Singleton
    fun providePurchasesRestStore(restApi: RestApi): PurchasesRestStore {
        return PurchasesRestStoreImpl(restApi)
    }

    @Provides
    @Singleton
    fun providePurchasesGoogleStore(
            rxBilling: RxBilling,
            mapper: PurchasesMapper
    ): PurchasesGoogleStore {
        return PurchasesGoogleStoreImpl(rxBilling, mapper)
    }

    @Provides
    fun providePurchaseMapper(): PurchasesMapper {
        return PurchasesMapperImpl()
    }

}
