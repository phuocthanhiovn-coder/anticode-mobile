# Anticode Mobile

Android client for [Anticode AI](https://anticode.vn) — AI-powered code editor.

## Features

- 🤖 **AI Chat** — Chat with AI coding assistant (streaming SSE)
- 📂 **File Explorer** — Browse, open, and edit files on device
- ✏️ **Code Editor** — Monospace editor with syntax-aware file detection
- ⚙️ **Settings** — API key, base URL, model selection
- 🎨 **Dark Theme** — Premium dark mode UI built with Jetpack Compose

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Networking:** OkHttp + Gson (SSE streaming)
- **Storage:** DataStore Preferences
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35

## Build

### Prerequisites
- JDK 17+
- Android SDK (API 35)

### Debug APK
```bash
# Linux/Mac
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Release APK
```bash
./gradlew assembleRelease
```

## Project Structure

```
app/src/main/java/vn/anticode/mobile/
├── MainActivity.kt          # Main UI + navigation
├── AnticodeApp.kt            # Application class
├── ai/
│   └── AnticodeApi.kt        # API client (SSE streaming, models)
├── data/
│   ├── FileManager.kt        # File I/O operations
│   └── SettingsStore.kt      # DataStore preferences
└── ui/
    ├── chat/
    │   └── ChatPanel.kt      # Chat messages UI
    ├── files/
    │   └── FileExplorer.kt   # File browser UI
    ├── settings/
    │   └── SettingsScreen.kt  # Settings page
    └── theme/
        ├── Color.kt           # Color palette
        └── Theme.kt           # Material theme
```

## API

Connects to Anticode server at `https://anticode.vn`:
- `POST /v1/chat/completions` — AI chat (OpenAI-compatible, SSE)
- `GET /api/user/models` — Available models (filtered by plan)
- `GET /v1/models` — Public model list (fallback)

Auth: `Authorization: Bearer ak-xxxxx`

## License

Proprietary — Anticode AI © 2026
