package com.appsci.panda.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.appsci.panda.sdk.Panda
import com.appsci.panda.sdk.PandaEvent
import com.appsci.panda.sdk.SimplePandaListener
import com.appsci.panda.sdk.ui.ScreenExtra
import com.appsci.panda.sdk.ui.SubscriptionFragment
import com.appsci.panda.sdk.ui.addPayload
import kotlinx.coroutines.launch
import org.json.JSONObject
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
        lifecycleScope.launch {
            Panda.getSubscriptionScreen(id = "id")
                .let {
                    val payloadMap = mapOf(
                        "target_language" to "ru",
                        "from_language" to "en",
                        "email" to "",
                        "custom_id" to "custom_id",
                    )
                    val fragment = SubscriptionFragment.create(ScreenExtra.create(it))
                    fragment.addPayload(JSONObject(payloadMap))
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.container, fragment)
                        .commitNow()
                }
        }

        Panda.addAnalyticsListener(analyticsListener)
        Panda.addListener(pandaListener)
    }

    override fun onDestroy() {
        Panda.removeAnalyticsListener(analyticsListener)
        Panda.removeListener(pandaListener)
        super.onDestroy()
    }
}
