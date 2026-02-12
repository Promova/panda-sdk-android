package com.appsci.panda.sdk.data.subscriptions.google

interface BillingValidator {
    suspend fun validateIntent()
}
