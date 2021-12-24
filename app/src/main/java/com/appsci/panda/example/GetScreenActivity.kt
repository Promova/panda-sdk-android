package com.appsci.panda.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.appsci.panda.sdk.Panda
import com.appsci.panda.sdk.PandaEvent
import com.appsci.panda.sdk.SimplePandaListener
import com.appsci.panda.sdk.domain.utils.rx.DefaultSingleObserver
import timber.log.Timber

class GetScreenActivity : AppCompatActivity() {

    companion object {
        fun createIntent(activity: Activity) =
                Intent(activity, GetScreenActivity::class.java)
    }

    private val analyticsListener: (PandaEvent) -> Unit = {
        Timber.d("PandaEvent $it")
    }
    private val pandaListener = object : SimplePandaListener() {
        override fun onDismissClick() {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_get_screen)
        Panda.getSubscriptionScreenRx(id = "feda684d-01bc-4855-a54e-3e23539e610f")
                .doOnSuccess {
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.container, it)
                            .commitNow()
                }
                .subscribe(DefaultSingleObserver())

        Panda.addAnalyticsListener(analyticsListener)
        Panda.addListener(pandaListener)
    }

    override fun onDestroy() {
        Panda.removeAnalyticsListener(analyticsListener)
        Panda.removeListener(pandaListener)
        super.onDestroy()
    }
}
