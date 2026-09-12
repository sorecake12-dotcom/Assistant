# Assistant (Powered by JARVIS) ⚡

[![Build & Publish Release APK](https://github.com/sorecake12-dotcom/Assistant/actions/workflows/release.yml/badge.svg)](https://github.com/sorecake12-dotcom/Assistant/actions/workflows/release.yml)
[![GitHub release](https://img.shields.io/github/v/release/sorecake12-dotcom/Assistant?color=00d4ff&logo=github)](https://github.com/sorecake12-dotcom/Assistant/releases)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-00f2fe)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

An intelligent, real-time voice and vision Android assistant powered by Google Gemini Live. Designed with an iconic Arc-Reactor interface, native Android hardware integrations, dynamic SIM card detection, and full hands-free operation.

---

## 🌟 Key Features

### 🎙️ Google Gemini Live Multimodal AI
- **15 Official Google Gemini Voices**: Choose from `Aoede`, `Charon`, `Fenrir`, `Kore`, `Puck`, `Leda`, `Orus`, `Zephyr`, `Vega`, `Lyra`, `Castor`, `Pollux`, `Oberon`, `Chiron`, and `Pegasi`.
- **Bidirectional Audio**: Ultra-low latency voice conversations via real-time WebSocket streaming.
- **Context-Aware Personalities**: Toggle between **AI Assistant (JARVIS)**, **Professional**, and **Companion** modes with tailored speech prompts.

### 📱 Dynamic SIM Card Detection
- **Smart SIM Slots**: Automatically detects your device's active SIM cards via Android's `SubscriptionManager` & `TelephonyManager`.
  - **Single SIM Phone**: Displays only **SIM 1**.
  - **Dual SIM Phone**: Displays both **SIM 1** and **SIM 2** with real-time carrier names.
- Direct SIM slot routing for outgoing phone calls and voice automations.

### 🚀 First-Launch Onboarding Experience
- Clean 3-step interactive onboarding flow:
  1. **Welcome Screen**: Introduces Assistant and core capabilities.
  2. **Permissions Hub**: Explicitly requests microphone, phone, camera, and overlay permissions.
  3. **AI Setup**: Masked API key entry with visibility eye toggle and **live test connection** validation.

### 🔄 Intelligent Conversation Lifecycle
- **Active Conversation**: Real-time voice interaction with animated audio reactor orb.
- **30s Silence Detection**: Automatically enters energy-efficient **Silent Mode**.
- **Instant Wake Word**: Resumes immediately upon hearing *"Hello Assistant"* / *"Hello [Your Name]"*.
- **Session Timeout**: Automatically ends voice session after 2 minutes of idle time to conserve battery.

### 🎨 Premium Iron Man Inspired Themes
- 💠 **Arc Blue** (Default Neon Cyan & Deep Obsidian)
- 👑 **Amber Gold** (Stark Mark XLII Champagne Gold)
- 🔴 **Crimson Red** (Hot Rod Titanium Red)

### 👁️ Camera Vision & System Automation
- Real-time CameraX preview for visual question-answering.
- Android Accessibility Service integration for UI navigation and device control.

---

## 📥 Download & Installation

### Option 1: Download Pre-built APK (Recommended)
Pre-built APKs are compiled automatically for every release using GitHub Actions:

👉 **[Download Latest APK from GitHub Releases](https://github.com/sorecake12-dotcom/Assistant/releases)**

1. Download `app-debug.apk` onto your Android phone.
2. Open the file and allow installation from unknown sources when prompted.
3. Launch **Assistant** and follow the onboarding guide.

---

## 🛠️ Building from Source

### Prerequisites
- **JDK 17** (e.g. Eclipse Adoptium Temurin 17)
- **Android SDK** (API Level 34, Build Tools 34.0.0)
- **Gradle 8.9+** (included via `./gradlew`)

### Build Commands

```bash
# Clone the repository
git clone https://github.com/sorecake12-dotcom/Assistant.git
cd Assistant

# Build Debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚙️ Automated Releases via GitHub Actions

This repository includes an automated CI/CD workflow (`.github/workflows/release.yml`):

- **Automatic Releases on Version Tags**: Push a tag starting with `v` (e.g., `git tag -a v1.1.5 -m "Release v1.1.5" && git push origin v1.1.5`) and GitHub Actions will automatically compile the APK and create a published release.
- **Manual Trigger**: Go to **Actions** → **Build & Publish Release APK** → **Run workflow** and specify your version tag.

---

## 🔒 Permissions & Privacy

Assistant uses standard Android permissions exclusively for requested operations:
- `RECORD_AUDIO`: Voice input for Google Gemini Live conversations.
- `CAMERA`: Vision analysis when camera mode is activated.
- `CALL_PHONE` & `READ_PHONE_STATE`: Dynamic SIM detection and outbound hands-free dialing.
- `SYSTEM_ALERT_WINDOW`: Screen overlay assistance.
- `BIND_ACCESSIBILITY_SERVICE`: System navigation assistance (optional).

API keys and user preferences are stored securely on-device using Android encrypted preferences.

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
