package com.appsci.panda.sdk.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.QueryProductDetailsParams
import com.appsci.billingktx.client.BillingKtx
import com.appsci.billingktx.client.PurchasesUpdate
import com.appsci.billingktx.lifecycle.keepConnection
import com.appsci.panda.sdk.Panda
import com.appsci.panda.sdk.R
import com.appsci.panda.sdk.databinding.PandaFragmentSubscriptionBinding
import com.appsci.panda.sdk.domain.subscriptions.SubscriptionScreen
import com.appsci.panda.sdk.domain.subscriptions.SubscriptionsRepository
import com.appsci.panda.sdk.domain.utils.getStringOrNull
import com.appsci.panda.sdk.domain.utils.rx.DefaultSingleObserver
import com.appsci.panda.sdk.domain.utils.rx.Schedulers
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

class SubscriptionFragment : Fragment() {

    @Inject
    lateinit var billingKtx: BillingKtx

    @Inject
    lateinit var subscriptionsRepository: SubscriptionsRepository

    private val disposeOnDestroyView = CompositeDisposable()

    private var _binding: PandaFragmentSubscriptionBinding? = null
    private val binding: PandaFragmentSubscriptionBinding
        get() = _binding!!

    private var onSuccessfulPurchase: (() -> Unit)? = null
    private val onPurchaseListener: (String) -> Unit = {
        onSuccessfulPurchase?.invoke()
    }

    private val screenExtra: ScreenExtra by lazy {
        requireArguments().getParcelable(EXTRA_SCREEN)!!
    }

    private val screenPayload: JSONObject? by lazy {
        requireArguments().getString(EXTRA_PAYLOAD)?.let {
            JSONObject(it)
        }
    }

    private var showTrialCompletable = CompletableDeferred<Boolean>()

    companion object {
        const val EXTRA_SCREEN = "screenExtra"
        const val EXTRA_PAYLOAD = "screenPayload"

        fun create(screenExtra: ScreenExtra) = SubscriptionFragment().apply {
            arguments = Bundle().apply {
                putParcelable(EXTRA_SCREEN, screenExtra)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Panda.pandaComponent.inject(this)
        billingKtx.keepConnection(this)

        lifecycleScope.launch {
            try {
                withTimeout(5000) {
                    val purchases = billingKtx.getPurchases(BillingClient.ProductType.SUBS)
                    showTrialCompletable.complete(purchases.isEmpty())
                }
            } catch (e: Exception) {
                showTrialCompletable.complete(false)
                Timber.d("cannot load subscriptions' purchase history in time")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return PandaFragmentSubscriptionBinding.inflate(inflater).apply {
            _binding = this
        }.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    binding.webView.evaluateJavascript("onBackPressed();") {
                        val handled = it.toBoolean()
                        Timber.d("onBackPressed result $it")
                        if (!handled) {
                            Panda.onBackClick(screenExtra)
                        }
                    }
                }
            },
        )


        binding.webView.setBackgroundColor(
            ContextCompat.getColor(
                requireContext(),
                R.color.panda_screen_bg
            )
        )
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.isHorizontalScrollBarEnabled = false
        binding.webView.isVerticalScrollBarEnabled = false

        // set on view created, remove on destroy view
        Panda.addPurchaseListener(onPurchaseListener)

        val jsBridge = object : JavaScriptBridgeInterface {
            override fun onPurchase(json: String) {
                Timber.d("onPurchase $json")
                onSuccessfulPurchase = null
                val obj = JSONObject(json)
                val productId = obj.getString("product_id")
                val type = obj.getStringOrNull("type")
                val url = obj.getStringOrNull("url")

                if (type == "external" && url != null) {
                    onSuccessfulPurchase = {
                        openExternalUrl(url)
                    }
                }
                if (type == "moveNext" && url != null) {
                    onSuccessfulPurchase = {
                        Timber.d("moveNext $url")
                        binding.webView.evaluateJavascript("moveNext();") {
                            Timber.d("moveNext result $it")
                        }
                    }
                }
                purchaseClick(productId)
            }

            override fun onScreenChanged(json: String) {
                Timber.d("onScreenChanged $json")
                val name = JSONObject(json).getString("screen_name")
                Panda.onScreenChanged(
                    id = screenExtra.id,
                    screenName = name,
                )
            }

            override fun onRedirect(json: String) {
                Timber.d("onRedirect $json")
                val url = JSONObject(json).getString("url")

                Panda.onRedirect(screenExtra.id, url)
                openExternalUrl(url)
            }

            override fun onCustomEventSent(json: String) {
                Timber.d("onCustomEventSent $json")
                val jsonObject = JSONObject(json)
                val params = runCatching {
                    jsonObject.getJSONObject("params")
                }.getOrNull()
                val paramsMap = mutableMapOf<String, String>()
                params?.keys()?.forEach { key ->
                    params.getStringOrNull(key)?.let { value ->
                        paramsMap[key] = value
                    }
                }
                Panda.onCustomEvent(
                    screenId = screenExtra.id,
                    name = jsonObject.getString("name"),
                    params = paramsMap,
                )
            }

            override fun loadPricing(request: String) {
                this@SubscriptionFragment.loadPricing(request)
            }

            override fun onAction(json: String) {
                Timber.d("onAction $json")
                val jsonObject = JSONObject(json)
                Panda.onAction(
                    name = jsonObject.getString("name"),
                    json = json,
                )
            }

            override fun onTerms() {
                Timber.d("onTerms")
                Panda.onTermsClick()
                openExternalUrl(getString(R.string.panda_terms_url))
            }

            override fun onPolicy() {
                Timber.d("onPolicy")
                Panda.onPolicyClick()
                openExternalUrl(getString(R.string.panda_policy_url))
            }

            override fun onDismiss() {
                Timber.d("onDismiss")
                Panda.onDismiss(screenExtra)
            }

            override fun onShowCloseConfirmation() {
                Timber.d("onShowCloseConfirmation")
                Panda.onShowCloseConfirmation(screenExtra)
            }

            override fun onRestore() {
                Timber.d("onRestore")
                restore()
            }
        }

        binding.webView.addJavascriptInterface(
            JavaScriptInterface(jsBridge),
            "AndroidFunction",
        )

        binding.webView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Timber.d("onPageFinished $url")
                _binding?.webView?.let { webView ->
                    webView.evaluateJavascript("setPayload($screenPayload);") {
                        Timber.d("setPayload result $it")
                    }
                }
                /**
                 * @Deprecated
                 * used for backward compatibility
                 */
                lifecycleScope.launchWhenStarted {
                    if (!showTrialCompletable.await()) {
                        _binding?.webView?.evaluateJavascript("removeTrialUi();") {
                            Timber.d("removeTrialUi result $it")
                        }
                    }
                }

                lifecycleScope.launchWhenStarted {
                    val showTrial = showTrialCompletable.await()
                    _binding?.webView?.evaluateJavascript("showTrial($showTrial);") {
                        Timber.d("showTrial result $it")
                    }
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest,
            ): Boolean {
                Timber.d("shouldOverrideUrlLoading1 ${request.url}")
                return handleRedirect(request.url.toString())
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                Timber.d("shouldOverrideUrlLoading2 $url")
                return handleRedirect(url)
            }

        }

        // Observe purchase updates using Flow
        lifecycleScope.launch {
            billingKtx.observeUpdates()
                .catch { e ->
                    Timber.e(e)
                    Panda.onError(e)
                }
                .collect { update ->
                    when (update) {
                        is PurchasesUpdate.Success -> {
                            val purchase = update.purchases.firstOrNull()
                            if (purchase != null) {
                                Timber.d("observeSuccess $update")
                                binding.loading.root.visibility = View.VISIBLE
                                val productId = purchase.products.firstOrNull() ?: ""
                                Panda.onPurchase(screenExtra, purchase, getType(productId))
                                    .doAfterTerminate {
                                        binding.loading.root.visibility = View.GONE
                                    }
                                    .subscribe({ success ->
                                        Timber.d("onPurchase success=$success")
                                    }, { error ->
                                        Panda.onError(error)
                                        Timber.e(error)
                                    })
                            }
                        }
                        is PurchasesUpdate.Failed -> {
                            val throwable = RuntimeException("Billing update error: code=${update.code}")
                            Timber.e(throwable)
                            Panda.onError(throwable)
                        }
                        is PurchasesUpdate.Canceled -> {
                            Timber.d("Purchase cancelled")
                        }
                    }
                }
        }

        disposeOnDestroyView.add(
            subscriptionsRepository.getCachedOrDefaultScreen(screenExtra.id)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.mainThread())
                .doOnSuccess { screen ->
                    binding.webView.loadDataWithBaseURL(
                        "file:///android_asset/",
                        screen.screenHtml,
                        null,
                        null,
                        null
                    )
                }
                .subscribeWith(DefaultSingleObserver()))
        Panda.screenShowed(screenExtra)
    }

    override fun onDestroyView() {
        _binding = null
        Panda.removePurchaseListener(onPurchaseListener)
        disposeOnDestroyView.clear()
        super.onDestroyView()
    }

    private fun loadPricing(requestString: String) {
        val gson = Gson()
        val requests: Map<String, List<String>> = gson.fromJson<List<ProductPricingRequest>>(
            requestString,
            object : TypeToken<List<ProductPricingRequest>>() {}.type,
        ).groupBy { it.type }
            .map { entry ->
                entry.key to entry.value.map { it.id }
            }.toMap()

        lifecycleScope.launch {
            runCatching {
                Panda.getProductsDetails(requests)
            }.onSuccess {
                val json = gson.toJson(it.toModels())
                lifecycleScope.launchWhenStarted {
                    binding.webView.evaluateJavascript("pricingLoaded($json);") {

                    }
                }
            }.onFailure {
                Timber.e(it)
            }
        }
    }

    private fun handleRedirect(url: String): Boolean {
        return when {
            url.contains("/subscription?type=restore") -> {
                Timber.d("restore click")
                restore()
                true
            }

            url.contains("/subscription?type=terms") -> {
                Timber.d("terms click")
                Panda.onTermsClick()
                openExternalUrl(getString(R.string.panda_terms_url))
                true
            }

            url.contains("/subscription?type=policy") -> {
                Timber.d("policy click")
                Panda.onPolicyClick()
                openExternalUrl(getString(R.string.panda_policy_url))
                true
            }

            url.contains("/subscription?type=purchase") -> {
                val id = url.toUri().getQueryParameter("product_id")
                    ?: error("product_id should be provided")

                purchaseClick(id)
                true
            }

            url.contains("/dismiss?type=dismiss") -> {
                Timber.d("dismiss click")
                Panda.onDismiss(screenExtra)
                true
            }

            else -> false
        }
    }

    private fun restore() {
        binding.loading.root.visibility = View.VISIBLE
        lifecycleScope.launch {
            runCatching {
                val list = Panda.restore(screenExtra)
                Timber.d("restore $list")
            }
            binding.loading.root.visibility = View.GONE
        }
    }

    private fun getType(id: String): String {
        val subscriptions = resources.getStringArray(R.array.panda_subscriptions)
        val products = resources.getStringArray(R.array.panda_products)
        return when {
            products.contains(id) -> {
                BillingClient.ProductType.INAPP
            }

            else -> BillingClient.ProductType.SUBS
        }
    }

    private fun purchaseClick(id: String) {
        Panda.subscriptionSelect(screenExtra, id)
        Timber.d("purchase click $id")
        val type = getType(id)

        lifecycleScope.launch {
            try {
                val productList = listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(type)
                        .build()
                )

                val query = QueryProductDetailsParams.newBuilder()
                    .setProductList(productList)
                    .build()

                val params = withContext(Dispatchers.IO) {
                    billingKtx.getProductDetails(query).map { productDetails ->
                        // For One-time products, "setOfferToken" method shouldn't be called.
                        val offerToken = if (type == BillingClient.ProductType.SUBS) {
                            productDetails.subscriptionOfferDetails
                                // to ensure prioritization of subscription with offer(trial or intro payment)
                                ?.firstOrNull { it.offerId != null }?.offerToken
                                ?: productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
                        } else {
                            null
                        }

                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .apply {
                                if (offerToken != null) {
                                    setOfferToken(offerToken)
                                }
                            }
                            .build()
                    }
                }

                if (params.isNotEmpty()) {
                    billingKtx.launchFlow(
                        activity = requireActivity(),
                        params = BillingFlowParams.newBuilder()
                            .setProductDetailsParamsList(params)
                            .build()
                    )
                } else {
                    val error = RuntimeException("Product details not found for $id")
                    Panda.onError(error)
                    Timber.e(error)
                }
            } catch (e: Exception) {
                Panda.onError(e)
                Timber.e(e)
            }
        }
    }

    private fun openExternalUrl(url: String) {
        Panda.onOpenExternal(screenExtra.id, url)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            Timber.e(e)
        }
    }

}

@Parcelize
data class ScreenExtra(
    val id: String,
    val name: String,
) : Parcelable {
    companion object {
        fun create(screen: SubscriptionScreen) =
            ScreenExtra(
                id = screen.id,
                name = screen.name
            )
    }
}

fun SubscriptionFragment.addPayload(json: JSONObject) {
    val args = this.arguments ?: Bundle()
    args.putString(SubscriptionFragment.EXTRA_PAYLOAD, json.toString())
}
