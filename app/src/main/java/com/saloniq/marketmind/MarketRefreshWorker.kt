package com.saloniq.marketmind

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class MarketRefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        val repository = MarketRepository(applicationContext)
        repository.refreshAll()
        val settings = SettingsStore(applicationContext)
        if (settings.notificationsEnabled()) {
            val asset = settings.loadAssets().firstOrNull()
            if (asset != null) {
                val quote = repository.cachedQuote(asset.symbol)
                val change = quote?.change24h?.let { String.format("%+.2f%%", it) } ?: "no change data"
                NotificationHelper.show(applicationContext, "MarketMind • ${asset.symbol}", "Price: ${quote?.price ?: "—"} • 24h: $change")
            }
        }
    }.fold({ Result.success() }, { Result.retry() })
}
