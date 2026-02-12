package com.appsci.panda.sdk.data

import okhttp3.OkHttpClient
import javax.inject.Inject

class StopNetwork @Inject constructor(
        private val okHttpClient: OkHttpClient
) {
    operator fun invoke() {
        okHttpClient.dispatcher.cancelAll()
        okHttpClient.cache?.evictAll()
    }
}
