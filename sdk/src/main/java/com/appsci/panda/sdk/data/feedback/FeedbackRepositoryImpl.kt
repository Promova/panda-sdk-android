package com.appsci.panda.sdk.data.feedback

import com.appsci.panda.sdk.data.device.DeviceDao
import com.appsci.panda.sdk.data.network.PandaApi
import com.appsci.panda.sdk.domain.feedback.FeedbackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FeedbackRepositoryImpl @Inject constructor(
    private val pandaApi: PandaApi,
    private val deviceDao: DeviceDao,
) : FeedbackRepository {

    override suspend fun sendFeedback(
        screenId: String,
        answer: String,
    ) = withContext(Dispatchers.IO) {
        val userId = deviceDao.requireUserId().await()
        pandaApi.sendFeedback(
            FeedbackRequest(
                userId = userId,
                screenId = screenId,
                answer = answer,
            )
        )
    }
}
