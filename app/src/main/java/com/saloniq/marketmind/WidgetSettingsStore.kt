package com.saloniq.marketmind

import android.content.Context

/** Stores the appearance of the home-screen widgets locally. */
data class WidgetSettings(
    val backgroundColor: String = "#CC111111",
    val textColor: String = "#FFFFFFFF",
    val accentColor: String = "#FF64B5F6",
    val positiveColor: String = "#FF4CAF50",
    val negativeColor: String = "#FFF44336",
    val backgroundAlpha: Int = 80,
    val textSize: Float = 14f,
    val priceSize: Float = 20f,
    val changeSize: Float = 13f,
    val cornerRadius: Int = 16,
    val padding: Int = 12,
    val showPrice: Boolean = true,
    val showChange: Boolean = true,
    val showName: Boolean = true,
    val showSignal: Boolean = true,
    val showUpdatedAt: Boolean = false
)

class WidgetSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("marketmind_widget", Context.MODE_PRIVATE)

    fun load(): WidgetSettings = WidgetSettings(
        backgroundColor = prefs.getString("background", "#CC111111") ?: "#CC111111",
        textColor = prefs.getString("text", "#FFFFFFFF") ?: "#FFFFFFFF",
        accentColor = prefs.getString("accent", "#FF64B5F6") ?: "#FF64B5F6",
        positiveColor = prefs.getString("positive", "#FF4CAF50") ?: "#FF4CAF50",
        negativeColor = prefs.getString("negative", "#FFF44336") ?: "#FFF44336",
        backgroundAlpha = prefs.getInt("alpha", 80),
        textSize = prefs.getFloat("text_size", 14f),
        priceSize = prefs.getFloat("price_size", 20f),
        changeSize = prefs.getFloat("change_size", 13f),
        cornerRadius = prefs.getInt("radius", 16),
        padding = prefs.getInt("padding", 12),
        showPrice = prefs.getBoolean("show_price", true),
        showChange = prefs.getBoolean("show_change", true),
        showName = prefs.getBoolean("show_name", true),
        showSignal = prefs.getBoolean("show_signal", true),
        showUpdatedAt = prefs.getBoolean("show_updated", false)
    )

    fun save(s: WidgetSettings) {
        prefs.edit()
            .putString("background", s.backgroundColor)
            .putString("text", s.textColor)
            .putString("accent", s.accentColor)
            .putString("positive", s.positiveColor)
            .putString("negative", s.negativeColor)
            .putInt("alpha", s.backgroundAlpha.coerceIn(0, 100))
            .putFloat("text_size", s.textSize.coerceIn(10f, 24f))
            .putFloat("price_size", s.priceSize.coerceIn(14f, 32f))
            .putFloat("change_size", s.changeSize.coerceIn(10f, 22f))
            .putInt("radius", s.cornerRadius.coerceIn(0, 32))
            .putInt("padding", s.padding.coerceIn(4, 24))
            .putBoolean("show_price", s.showPrice)
            .putBoolean("show_change", s.showChange)
            .putBoolean("show_name", s.showName)
            .putBoolean("show_signal", s.showSignal)
            .putBoolean("show_updated", s.showUpdatedAt)
            .apply()
    }

    fun reset() = save(WidgetSettings())
}
