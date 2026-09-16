# MarketMind Android

MarketMind is an Android app for monitoring stocks and cryptocurrencies, calculating technical indicators, and generating market summaries with NVIDIA NIM.

## Planned features

- Stocks and crypto in one watchlist
- Add multiple assets
- Price and 24h change monitoring
- RSI, MACD, SMA/EMA, Bollinger Bands and volatility
- Potential entry signals based on configurable rules
- NVIDIA NIM market summaries
- Android notifications
- Customizable home-screen widgets
- Periodic background updates with WorkManager
- GitHub Actions APK builds

## Development

Open this repository in Android Studio and use JDK 17.

The NVIDIA API key will not be hardcoded into the application. It will be added through app configuration or a secure backend in a later step.

## Status

Early development: the first Compose dashboard and CI build pipeline are in place. Market APIs, indicator calculations, NIM integration, persistence, notifications and widgets are being implemented next.

> Market signals are informational and are not guarantees about future prices.
