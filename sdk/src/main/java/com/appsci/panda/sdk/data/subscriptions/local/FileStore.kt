package com.appsci.panda.sdk.data.subscriptions.local

import android.content.Context
import com.appsci.panda.sdk.domain.subscriptions.SubscriptionScreen

interface FileStore {
    suspend fun getSubscriptionScreen(): SubscriptionScreen
}

class FileStoreImpl(
        private val context: Context
) : FileStore {

    override suspend fun getSubscriptionScreen(): SubscriptionScreen {
        val html = context.assets.open("panda-index.html")
                .bufferedReader()
                .use { it.readText() }
        return SubscriptionScreen(
                id = "",
                name = "",
                screenHtml = html
        )
    }
}
