package com.appsci.panda.sdk.data.subscriptions.google

import io.reactivex.Completable
import io.reactivex.Single
import java.security.MessageDigest
import java.util.*

class BillingValidatorImpl : BillingValidator {
    companion object {
        const val ACTION_HASH = "28C84117EB0449B618A288AF52A21C3A7C0A0BFD0EA525CF8CD7D8AC38B59E55"
        const val PACKAGE_HASH = "6BC560052007DFB4486F378DB708538F170F77123C84987201F47CC30D994169"
        const val BIND_ACTION = "com.android.vending.billing.InAppBillingService.BIND"
        const val PACKAGE = "com.android.vending"
    }

    override fun validateIntent(): Completable {
        return Single.fromCallable {
            val digest = MessageDigest.getInstance("SHA-256")
            val actionBytes = digest.digest(BIND_ACTION.toByteArray())
            val packageBytes = digest.digest(PACKAGE.toByteArray())
            val currentActionHash = actionBytes.fold("") { str, it -> str + "%02x".format(it) }
                    .uppercase(Locale.US)
            val currentPackageHash = packageBytes.fold("") { str, it -> str + "%02x".format(it) }
                    .uppercase(Locale.US)
            return@fromCallable currentActionHash == ACTION_HASH && currentPackageHash == PACKAGE_HASH
        }
                .onErrorReturnItem(true)
                .flatMapCompletable {
                    return@flatMapCompletable when (it) {
                        true -> Completable.complete()
                        false -> Completable.error(
                                InvalidIntentException(action = BIND_ACTION, packageName = PACKAGE))
                    }
                }

    }
}
