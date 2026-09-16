package com.saloniq.marketmind

data class Asset(
    val symbol: String,
    val name: String,
    val type: String,
    val marketSymbol: String = symbol,
    val coinId: String? = null
)

data class Quote(
    val price: Double? = null,
    val change24h: Double? = null,
    val rsi: Double? = null,
    val signal: String = "Waiting for analysis",
    val updatedAt: Long = System.currentTimeMillis()
)
