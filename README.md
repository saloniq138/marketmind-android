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

## Current project setup

- Android Gradle Plugin: 8.7.3
- Kotlin: 2.0.21
- Gradle: 8.9
- JDK: 17
- compileSdk / targetSdk: 35
- minSdk: 26
- Jetpack Compose + Material 3

## Build locally

### Android Studio

1. Install Android Studio and JDK 17.
2. Clone this repository and open the repository folder in Android Studio.
3. Let Gradle sync.
4. Select an emulator or connected Android phone.
5. Run the `app` configuration.
6. To create an APK, use **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

### Linux/macOS terminal

The repository currently does not include the Gradle Wrapper, so install Gradle 8.9 first. Then run from the repository root:

```bash
gradle --version
gradle assembleDebug
```

The debug APK will be created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For a release build:

```bash
gradle assembleRelease
```

A release APK is not signed for Play Store distribution yet; signing will be added later.

## GitHub Actions

Every push to `main`, pull request to `main`, or manual workflow run builds a debug APK. The workflow uses JDK 17 and Gradle 8.9 and uploads `app-debug.apk` as the `marketmind-debug-apk` artifact.

## Security

The NVIDIA API key will not be hardcoded into the application. It will be added through app configuration or, preferably, a secure backend in a later step.

## Status

Early development: the first Compose dashboard and CI build pipeline are in place. Market APIs, indicator calculations, NIM integration, persistence, notifications and widgets are being implemented next.

> Market signals are informational and are not guarantees about future prices.
