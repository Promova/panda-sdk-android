package com.appsci.panda.example

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.appsci.panda.example.databinding.ActivityMainBinding
import com.appsci.panda.sdk.Panda
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding: ActivityMainBinding
        get() = _binding!!

    private val onError: (e: Throwable) -> Unit = {
        val msg = "panda error ${it.message}"
        Timber.d(msg)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(R.layout.activity_main)

        with(binding) {
            btnGetScreen.setOnClickListener {
                startActivity(GetScreenActivity.createIntent(this@MainActivity))
            }
        }
        lifecycleScope.launch {
            try {
                Panda.syncUser()
                Panda.saveCustomUserId(id = "super-unique-custom-id")
                val subscriptionState = Panda.getSubscriptionState()
                Timber.d("getSubscriptionState $subscriptionState")
                Panda.prefetchSubscriptionScreen(id = "id")
            }catch (e: Exception){
                Timber.e(e)
            }
            Panda.addErrorListener(onError)
        }
    }

    override fun onDestroy() {
        Panda.removeErrorListener(onError)
        super.onDestroy()
    }
}
