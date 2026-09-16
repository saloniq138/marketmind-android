package com.saloniq.marketmind

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class MarketRefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        MarketRepository(applicationContext).refreshAll()
    }.fold({ Result.success() }, { Result.retry() })
}
