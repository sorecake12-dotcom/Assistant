# Assistant ⚡ (v1.0.1 Release)

[![Build & Publish Release APK](https://github.com/sorecake12-dotcom/Assistant/actions/workflows/release.yml/badge.svg)](https://github.com/sorecake12-dotcom/Assistant/actions/workflows/release.yml)
[![GitHub release](https://img.shields.io/github/v/release/sorecake12-dotcom/Assistant?color=00d4ff&logo=github)](https://github.com/sorecake12-dotcom/Assistant/releases)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-00f2fe)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Assistant** is a state-of-the-art, real-time voice, vision, and automation Android assistant powered by Google Gemini Live. It features an interactive Arc-Reactor orb interface, native Android hardware integrations, dynamic SIM card detection, structured natural language action routing, and hands-free voice operation.

---

## 📋 System Requirements

- **Platform**: Android 8.0+ (API Level 26 or higher)
- **Target SDK**: Android 14 (API Level 34)
- **Architecture**: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- **JDK Requirement**: JDK 17 (e.g., Eclipse Adoptium Temurin 17)

---

## 🌟 Core Architecture & Features

### 1. Dual Identity Architecture
- **Application Name**: `Assistant` (persists on launcher, system settings, and app drawer).
- **Configurable Assistant Identity**: Defaults to `Jarvis`. Can be independently renamed in Settings (e.g., `Iris`, `Friday`, `Nova`).
- **Consistent Identity Propagation**: Changing the assistant name updates the main UI, header notch, chat drawer, wake word (`Hello <Name>`), silent mode announcements, notifications, and system prompts.
- **Configurable User Name**: Defaults to `Boss`, fully customizable.

### 2. Settings & Save/Apply Architecture
- **Strict Draft State Isolation**: Changes made in Settings remain temporary until **SAVE & APPLY** is tapped. Exiting without saving discards draft changes and restores the previous saved configuration.
- **Gemini API Key Security**: Masked by default with visibility toggle; securely saved in local app preferences; never committed to git, never logged, and never exposed in telemetry.
- **Automatic Key Validation**: Validates the Gemini API key against Google servers before saving.

### 3. Personality Matrices
Each personality mode alters the Gemini Live system instructions and responses:
- ⚡ **AI Assistant**: Concise, highly intelligent, mission-focused responses.
- ❤️ **GF Mode**: Warm, affectionate, empathetic, conversational companion tone.
- 💼 **Professional**: Formal, executive-level, structured brevity.
*(Rendered using clean, futuristic vector iconography).*

### 4. Real-time Gemini Live Voice
- Direct bi-directional streaming over WebSockets.
- Supports official Gemini voices including: `Kore`, `Puck`, `Charon`, `Fenrir`, `Zephyr`, `Aoede`, `Leda`, `Orus`.
- Applied seamlessly upon Save & Apply.

### 5. Tri-Theme System
- 💠 **Arc Blue**: Neon Cyan & Deep Space Obsidian
- 👑 **Amber Gold**: Stark Champagne Gold & Amber Glow
- 🔴 **Red**: Hot Rod Titanium Red & Ruby Glow
Smooth transitions dynamically update the 3D WebGL/Canvas orb, power buttons, floating camera frame, status indicators, and settings indicators.

### 6. Voice Session & Silent Mode Inactivity Cycle
- **Active Session**: Continuous conversation with animated orb state.
- **30-Second Inactivity**: Assistant announces:
  > *"I am going into silent mode. If you want me, just say, Hello [Assistant Name]."*
- **Silent Waiting State**: Background hotword listener awaits the configured wake phrase.
- **Wake Phrase Recovery**: Saying `"Hello <Name>"` immediately restores full active conversation.
- **2-Minute Inactivity Timeout**: If silence continues for 2 minutes in silent mode, assistant says:
  > *"Bye Boss"*
  and terminates the session completely.
- **Explicit Session Termination**: Commands like `"bye"`, `"goodbye"`, `"end call"`, `"stop session"` end the session immediately with `"Bye Boss"`.
- **Zero Background Spying**: When the microphone is muted or the session ends, all audio recording and wake-word detection are destroyed immediately.

### 7. Centralized Structured Action Router
Natural language commands are parsed into strongly-typed action classes:
- `OpenAppAction`: Launches apps by package name or label (`"Open Spotify"`, `"Open WhatsApp"`, `"Open Chrome"`, `"Open YouTube"`).
- `CloseAssistantAction`: Closes the assistant and returns to the Android home screen (`"Close the app"`).
- `GoHomeAction`: Navigates to Android Home (`"Go to home screen"`).
- `OpenAssistantHomeAction`: Brings Assistant main activity to the front (`"Go to Assistant home screen"`).
- `OpenPlayStoreAction`: Opens Google Play Store search/listing (`"Install Spotify"`, `"Download WhatsApp"`).
- `PlayMediaAction`: Controls YouTube and Spotify playback.
  - Contextual Resolution: `"Play Barsaat on YouTube"`, followed by `"Play that on Spotify"`. The assistant resolves `"that"` to `"Barsaat"` and switches platforms.
- `PauseMediaAction` / `ResumeMediaAction` / `NextMediaAction` / `PreviousMediaAction` / `StopMediaAction`: Media key events.
- `OpenUrlAction` / `OpenMultipleUrlsAction`: Opens single or multi-target websites in Chrome/browser tabs (`"Open Vercel Netflix and Prime Video"`).
- `CallContactAction` / `SendMessageAction` / `VideoCallAction`: Direct telephony, SMS, and WhatsApp integrations with contact lookup.
- `CameraAction` / `VisionAction`: Floating camera or MediaProjection screen capture.
- `AccessibilityAction`: Automation gestures via Accessibility service (`"scroll down"`, `"scroll up"`, `"go back"`, `"tap <Text>"`).
- `SettingsAction`: Opens configuration (`"Open settings"`).

### 8. CameraX Floating View
- Real device camera preview using CameraX.
- Draggable floating window with touch-drag listener.
- Camera flip (Front / Back lens switching).
- Flash / Torch toggle.
- Lifecycle-aware binding.

### 9. Screen Vision (MediaProjection)
- Requests native Android screen capture permission.
- Activates screen capture only when granted by user.
- Shows active status banner with clean resource release upon stopping.

### 10. Real Permission Hub & SIM Selection
- Real Android system checks for: Microphone, Camera, Contacts, Phone Calls, Phone State / Telephony, Notifications, Location, Accessibility, Overlay, and Battery Optimization.
- Dynamic SIM Detection: Automatically identifies SIM 1 and SIM 2 using `SubscriptionManager` and routes outgoing communication through the user's preferred SIM.

### 11. Persistent Conversation History
- Swipe from the **left edge toward the right** or tap the top title notch to reveal the full chat history drawer.
- Locally persisted in encrypted preferences across app restarts.

---

## 🛠️ Building & Testing

### 1. Run Automated Unit Tests
```powershell
.\gradlew.bat testDebugUnitTest
```

### 2. Build Release APK
```powershell
.\gradlew.bat assembleRelease
```
The compiled, signed release APK is output to:
`app/build/outputs/apk/release/Assistant-1.0.1-release.apk`

---

## 🔒 Security & Android Boundaries

- **Zero Hard-coded Secrets**: All API keys are user-configured and stored in encrypted on-device SharedPreferences.
- **Permission Transparency**: Runtime permissions follow standard Android OS dialogs; special permissions (Accessibility, Overlay, MediaProjection) route directly to official system settings screens.
- **Play Store Safety**: Installation requests route to the official Google Play Store app listing rather than performing background side-loading.

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
