# YT Auto Shorts 🎬⚡

**Production-ready native Android application that automates vertical YouTube Shorts creation directly on-device.**

YT Auto Shorts collects copyright-safe photos from Telegram channels, renders full HD 1080x1920 vertical MP4 video Shorts with pan/zoom/crossfade effects, generates viral titles, descriptions, and hashtags using AI, and uploads the finished Short to your YouTube channel using official YouTube Data API v3 and OAuth 2.0.

> 🚀 **100% Native Android APK**: No Termux, no Node.js, no Python, no VPS, and no external server required. Everything runs locally on your Android smartphone.

---

## 📑 Table of Contents

1. [Architecture & Technology Stack](#-architecture--technology-stack)
2. [Key Features](#-key-features)
3. [Installation & Setup](#-installation--setup)
4. [Telegram Bot Configuration](#-telegram-bot-configuration)
5. [YouTube Data API & OAuth 2.0 Setup](#-youtube-data-api--oauth-20-setup)
6. [AI Provider Setup](#-ai-provider-setup)
7. [Automated Scheduling & Battery Optimization](#-automated-scheduling--battery-optimization)
8. [Troubleshooting Guide](#-troubleshooting-guide)
9. [Security & Privacy](#-security--privacy)

---

## 🏗 Architecture & Technology Stack

```
   ┌────────────────────────────────────────────────────────┐
   │                   YT Auto Shorts App                   │
   ├───────────────────┬───────────────────┬────────────────┤
   │  Telegram Sync    │  Video Generator  │  YouTube API   │
   │  Telegram Bot API │  MediaCodec/EGL   │  Resumable v3  │
   │  Long Polling     │  1080x1920 MP4    │  OAuth 2.0     │
   └─────────┬─────────┴─────────┬─────────┴────────┬───────┘
             │                   │                  │
             ▼                   ▼                  ▼
   ┌───────────────────┬───────────────────┬────────────────┐
   │   Room Database   │  AI Meta Engine   │ Work / Alarms  │
   │  Profiles & Photos│  Gemini / You.com │ Exact Alarms   │
   │  Encrypted Keystore│ OpenAI / Custom   │ WorkManager    │
   └───────────────────┴───────────────────┴────────────────┘
```

- **Framework**: Jetpack Compose + Material Design 3
- **Local Persistence**: Room SQLite DB (ProfileDao, PhotoDao, JobDao)
- **Security**: Android Keystore AES-256 GCM (`EncryptedSharedPreferences`)
- **Video Rendering Engine**: Native Android `MediaCodec` H.264 encoder + OpenGL ES 2.0 hardware surface pipeline
- **Networking**: OkHttp 4 + Kotlinx Coroutines
- **Background Tasks**: Android `AlarmManager` (Exact Alarms) + `WorkManager` (Expedited Workers) + `WakeLock`
- **Photo Picker**: Android Photo Picker API (`PickMultipleVisualMedia`) with zero broad storage permissions

---

## ✨ Key Features

- **Automated Telegram Sync**: Automatically fetches photos from your Telegram channels using Telegram Bot API.
- **Hardware-Accelerated Video Rendering**: Encodes vertical 1080x1920 (9:16) H.264 MP4 videos directly using device hardware without FFmpeg binaries.
- **Dynamic Visual Effects**: Slow zoom in/out (Ken Burns), smooth left-to-right panning, and crossfade transitions between slides.
- **Multi-Provider AI Engine**: Automatic fallback chain from Primary Provider (Gemini / You.com / OpenAI) to Backup Provider to Local Fallback engine.
- **Resumable YouTube Uploads**: Uploads video files directly to YouTube with live progress tracking, category assignment, and privacy controls (Private/Unlisted/Public).
- **Multi-Profile Automation**: Manage multiple automated channels (e.g. Gaming Shorts, History Facts, Tech News) with distinct schedules.
- **Built-in Video Player**: Watch and review rendered Shorts in-app with looping video playback.
- **Offline & Reboot Resilient**: `BootReceiver` restores alarm schedules automatically after phone restarts.

---

## 📲 Installation & Setup

1. Download the latest debug APK from the GitHub Actions build artifact or build locally via Android Studio.
2. Install the APK on your Android device (Android 8.0+ / API 26+).
3. Open **YT Auto Shorts**.
4. In **Settings**, configure your:
   - Telegram Bot Token & Channel Username
   - AI Provider (Gemini, You.com, or OpenAI)
   - YouTube OAuth Client ID & Secret
5. Enable **Automation** and grant battery optimization exemptions.

---

## 🤖 Telegram Bot Configuration

1. Open Telegram and search for `@BotFather`.
2. Send `/newbot` and follow the prompts to choose a name and username (e.g., `MyShortsCollectorBot`).
3. Copy the HTTP API token provided by BotFather (looks like `7123456789:ABCdefGhIJKlmnoPQRsTUVwxyZ`).
4. Go to your Telegram channel:
   - Tap Channel Settings ➔ **Administrators** ➔ **Add Administrator**.
   - Search for your bot username and add it as an Admin with **Post Messages** permission (or standard view permissions).
5. Open **YT Auto Shorts** ➔ **Settings** ➔ **Telegram**:
   - Paste the Bot Token.
   - Enter your channel username (e.g., `@mychannel`) or numeric Chat ID (e.g., `-100123456789`).
   - Tap **Test Bot & Channel** to verify.
   - Tap **Sync Now** to download existing channel photos.

---

## 📺 YouTube Data API & OAuth 2.0 Setup

1. Open the [Google Cloud Console](https://console.cloud.google.com/).
2. Create a new project named **YT Auto Shorts**.
3. In **APIs & Services** ➔ **Library**, search for and enable **YouTube Data API v3**.
4. Configure the **OAuth Consent Screen**:
   - User Type: **External**.
   - App Name: `YT Auto Shorts`.
   - Add scopes: `https://www.googleapis.com/auth/youtube.upload` and `https://www.googleapis.com/auth/youtube.readonly`.
   - Add your Google account email under **Test Users**.
5. Create Credentials:
   - Navigate to **Credentials** ➔ **Create Credentials** ➔ **OAuth client ID**.
   - Application type: **Web Application** (or Android with redirect URI).
   - Authorized redirect URIs: Add `ytautoshorts://oauth2redirect`.
   - Copy the **Client ID** and **Client Secret**.
6. In **YT Auto Shorts** ➔ **Settings** ➔ **YouTube**:
   - Paste the Client ID and Client Secret.
   - Tap **Authorize Account** to open the browser consent page.
   - Authorize your YouTube channel and copy the authorization code.
   - Tap **Enter Code** and paste the code to exchange for refresh and access tokens.

---

## 🧠 AI Provider Setup

YT Auto Shorts supports Google Gemini, You.com, OpenAI, or any custom compatible endpoint:

### Google Gemini (Recommended)
1. Go to [Google AI Studio](https://aistudio.google.com/).
2. Generate an API Key.
3. Paste the key in **Settings** ➔ **AI** ➔ **Google Gemini API Key**.
4. Select **GEMINI** as your Primary Provider.

### You.com
1. Obtain an API key from [You.com Platform](https://you.com/).
2. Paste into **You.com API Key** and set as Primary or Backup.

### OpenAI / Compatible APIs
1. Enter your OpenAI API Key and Model (e.g., `gpt-4o-mini`).
2. Supports custom base URLs (e.g., Groq, Together, DeepSeek, or local Ollama).

---

## ⏰ Automated Scheduling & Battery Optimization

- Configure daily schedules in CSV format (e.g., `09:00,14:00,19:00`) in **Settings** ➔ **Schedule**.
- **Crucial**: Tap **Battery Optimization Settings** and select **Don't Optimize / Unrestricted** for YT Auto Shorts.
- Modern Android versions (Android 12+) put apps to sleep when the screen is turned off. Setting Unrestricted allows `AlarmManager.setExactAndAllowWhileIdle` to execute jobs on schedule.

---

## 🛠 Troubleshooting Guide

| Issue | Root Cause | Solution |
|---|---|---|
| **Bot cannot access channel** | Bot is not an admin or channel username has a typo | Ensure bot is added as Administrator in channel settings; verify username includes `@`. |
| **YouTube upload error 401** | OAuth token expired or revoked | Tap 'Authorize Account' in YouTube settings to refresh tokens. |
| **YouTube upload quota exceeded (403)** | Daily YouTube Data API v3 quota (10,000 units) reached | YouTube upload costs 1,600 units per video (~6 videos/day free). Request higher quota in Google Cloud Console if needed. |
| **Background alarms not firing** | Battery saver killed process | Add YT Auto Shorts to Battery Optimization whitelist in Android settings. |
| **Video rendering fails** | Corrupt image dimensions or unsupported codec | App includes automatic downsampling and RGB normalization. Ensure at least 3 valid photos exist in queue. |

---

## 🔒 Security & Privacy

- All sensitive tokens and credentials (Telegram Bot Token, YouTube Refresh Tokens, AI API Keys) are encrypted at rest using Android Keystore AES-256 GCM.
- Media files are stored in the app's internal sandboxed directory (`context.filesDir`).
- Photo access uses the native Android Photo Picker without requesting broad device storage permissions.
