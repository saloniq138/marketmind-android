package com.saloniq.marketmind

import android.content.Context

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
    private fun key(widget: String, name: String) = "${widget}_$name"

    fun load(widget: String = "market"): WidgetSettings = WidgetSettings(
        backgroundColor = prefs.getString(key(widget, "background"), "#CC111111") ?: "#CC111111",
        textColor = prefs.getString(key(widget, "text"), "#FFFFFFFF") ?: "#FFFFFFFF",
        accentColor = prefs.getString(key(widget, "accent"), "#FF64B5F6") ?: "#FF64B5F6",
        positiveColor = prefs.getString(key(widget, "positive"), "#FF4CAF50") ?: "#FF4CAF50",
        negativeColor = prefs.getString(key(widget, "negative"), "#FFF44336") ?: "#FFF44336",
        backgroundAlpha = prefs.getInt(key(widget, "alpha"), 80),
        textSize = prefs.getFloat(key(widget, "text_size"), 14f),
        priceSize = prefs.getFloat(key(widget, "price_size"), 20f),
        changeSize = prefs.getFloat(key(widget, "change_size"), 13f),
        cornerRadius = prefs.getInt(key(widget, "radius"), 16),
        padding = prefs.getInt(key(widget, "padding"), 12),
        showPrice = prefs.getBoolean(key(widget, "show_price"), true),
        showChange = prefs.getBoolean(key(widget, "show_change"), true),
        showName = prefs.getBoolean(key(widget, "show_name"), true),
        showSignal = prefs.getBoolean(key(widget, "show_signal"), true),
        showUpdatedAt = prefs.getBoolean(key(widget, "show_updated"), false)
    )

    fun save(widget: String = "market", s: WidgetSettings) {
        prefs.edit()
            .putString(key(widget, "background"), s.backgroundColor)
            .putString(key(widget, "text"), s.textColor)
            .putString(key(widget, "accent"), s.accentColor)
            .putString(key(widget, "positive"), s.positiveColor)
            .putString(key(widget, "negative"), s.negativeColor)
            .putInt(key(widget, "alpha"), s.backgroundAlpha.coerceIn(0, 100))
            .putFloat(key(widget, "text_size"), s.textSize.coerceIn(10f, 24f))
            .putFloat(key(widget, "price_size"), s.priceSize.coerceIn(14f, 32f))
            .putFloat(key(widget, "change_size"), s.changeSize.coerceIn(10f, 22f))
            .putInt(key(widget, "radius"), s.cornerRadius.coerceIn(0, 32))
            .putInt(key(widget, "padding"), s.padding.coerceIn(4, 24))
            .putBoolean(key(widget, "show_price"), s.showPrice)
            .putBoolean(key(widget, "show_change"), s.showChange)
            .putBoolean(key(widget, "show_name"), s.showName)
            .putBoolean(key(widget, "show_signal"), s.showSignal)
            .putBoolean(key(widget, "show_updated"), s.showUpdatedAt)
            .apply()
    }

    fun reset(widget: String = "market") = save(widget, WidgetSettings())
}
