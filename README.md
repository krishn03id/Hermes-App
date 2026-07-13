# Hermes

A simple, native **Android** chatbot with a Claude-inspired UI. Bring your own API
keys — OpenAI, Anthropic, and Gemini (plus any OpenAI-compatible endpoint) are
supported side by side.

Built with **Kotlin + Jetpack Compose**. No WebView, no bundled backend — every
request goes straight from your phone to the provider, and keys are stored only on
device (DataStore).

## Features

- 🗣️ Streaming chat with a clean, Claude-like interface (warm paper theme, light + dark).
- 📁 Collapsible sidebar to start a new chat and switch between configured models.
- 🔑 **Multiple API keys across multiple providers** — add as many as you like and
  tap to switch the active one.
- 🧩 **Providers:** OpenAI, Anthropic, Gemini. Point the OpenAI client at any
  compatible base URL (Groq, OpenRouter, local servers, …).
- 🧾 **Custom headers** per key — for gateways that need extra auth/routing headers.
- ⚙️ Per-key model + base URL configuration.

## Getting the APK

Every push to `main` builds a debug APK via GitHub Actions. Grab it from:

- **Actions → Build APK → Artifacts → `hermes-debug-apk`**, or
- the **`latest`** prerelease on the Releases page.

## Configure

1. Install and open the app.
2. Menu (top-left) → **Settings** → **+**.
3. Pick a provider, paste your API key, set the model, and (optionally) add custom
   headers. Save.
4. Add more keys the same way; tap **Use** on any to make it active.

## Build locally

Requires JDK 17 and the Android SDK (platform 35).

```bash
gradle wrapper --gradle-version 8.11.1   # first time only
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

## Project layout

```
app/src/main/java/id/krishn03/hermes/
├── MainActivity.kt
├── data/        Models, provider enum, on-device settings store
├── net/         LlmClient — streaming SSE for OpenAI / Anthropic / Gemini
└── ui/          Compose UI: chat, sidebar, settings, theme
```
