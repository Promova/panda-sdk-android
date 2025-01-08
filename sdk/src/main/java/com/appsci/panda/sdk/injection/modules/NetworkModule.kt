package com.appsci.panda.sdk.injection.modules

import android.content.Context
import android.content.pm.ApplicationInfo
import com.appsci.panda.sdk.BuildConfig
import com.appsci.panda.sdk.data.network.HeaderInterceptor
import com.appsci.panda.sdk.data.network.PandaApi
import com.appsci.panda.sdk.data.network.ScreenApi
import com.appsci.panda.sdk.domain.utils.DeviceManager
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
class NetworkModule(
        private val debug: Boolean,
        private val apiKey: String,
        private val networkLogLevel: HttpLoggingInterceptor.Level,
) {

    companion object {
        private const val CACHE_SIZE = 10L * 1024L * 1024L
        private const val CACHE_DIR_NAME = BuildConfig.LIBRARY_PACKAGE_NAME + "ResponseCache"

        private const val CLIENT_CONNECT_TIMEOUT_SECONDS = 30L
        private const val CLIENT_READ_TIMEOUT_SECONDS = 30L
        private const val CLIENT_WRITE_TIMEOUT_SECONDS = 10L
    }

    @Provides
    @Singleton
    fun provideCache(context: Context): Cache {
        return Cache(File(context.cacheDir, CACHE_DIR_NAME), CACHE_SIZE)
    }

    @Provides
    @Singleton
    fun provideOkHttpInterceptor(): HttpLoggingInterceptor {
        val interceptor = HttpLoggingInterceptor()
        interceptor.level = networkLogLevel
        return interceptor
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
            context: Context,
            cache: Cache,
            httpLoggingInterceptor: HttpLoggingInterceptor,
            deviceManager: DeviceManager
    ): OkHttpClient {

        val clientBuilder = OkHttpClient.Builder()
        val isDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (isDebuggable) {
            clientBuilder
                    .addNetworkInterceptor(httpLoggingInterceptor)
        }
        clientBuilder
                .cache(cache)
                .readTimeout(CLIENT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(CLIENT_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectTimeout(CLIENT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(HeaderInterceptor(deviceManager = deviceManager, apiKey))

        return clientBuilder.build()
    }

    @Provides
    @Singleton
    fun providePandaApi(okHttpClient: OkHttpClient): PandaApi {
        val gson = GsonBuilder()
                .setLenient()
                .create()
        return Retrofit.Builder()
                .baseUrl(if (debug) {
                    BuildConfig.PANDA_ENDPOINT_STAGE
                } else {
                    BuildConfig.PANDA_ENDPOINT_PROD
                })
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(okHttpClient)
                .build()
                .create(PandaApi::class.java)
    }

    @Provides
    @Singleton
    fun provideScreenApi(okHttpClient: OkHttpClient): ScreenApi {
        val gson = GsonBuilder()
            .setLenient()
            .create()
        return Retrofit.Builder()
            .baseUrl("https://isengard.promova-tech/")
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient)
            .build()
            .create(ScreenApi::class.java)
    }
}
