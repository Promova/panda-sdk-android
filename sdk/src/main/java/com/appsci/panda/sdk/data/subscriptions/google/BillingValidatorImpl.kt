package com.appsci.panda.sdk.data.subscriptions.google

import java.security.MessageDigest
import java.util.*

class BillingValidatorImpl : BillingValidator {
    companion object {
        const val ACTION_HASH = "28C84117EB0449B618A288AF52A21C3A7C0A0BFD0EA525CF8CD7D8AC38B59E55"
        const val PACKAGE_HASH = "6BC560052007DFB4486F378DB708538F170F77123C84987201F47CC30D994169"
        const val BIND_ACTION = "com.android.vending.billing.InAppBillingService.BIND"
        const val PACKAGE = "com.android.vending"
    }

    override suspend fun validateIntent() {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val actionBytes = digest.digest(BIND_ACTION.toByteArray())
            val packageBytes = digest.digest(PACKAGE.toByteArray())
            val currentActionHash = actionBytes.fold("") { str, it -> str + "%02x".format(it) }
                    .uppercase(Locale.US)
            val currentPackageHash = packageBytes.fold("") { str, it -> str + "%02x".format(it) }
                    .uppercase(Locale.US)
            val isValid = currentActionHash == ACTION_HASH && currentPackageHash == PACKAGE_HASH
            if (!isValid) {
                throw InvalidIntentException(action = BIND_ACTION, packageName = PACKAGE)
            }
        } catch (e: InvalidIntentException) {
            throw e
        } catch (_: Exception) {
            // Ignore errors, treat as valid (matching original onErrorReturnItem(true) behavior)
        }
    }
}
