# 🤖 MYRA AI Assistant — Complete Setup Guide

## ⚡ Quick Start (5 Steps)

### Step 1: Android Studio Setup
1. Download **Android Studio Hedgehog (2023.1.1)** or newer from https://developer.android.com/studio
2. Install with default settings
3. Make sure **Android SDK 26+** is installed (SDK Manager → SDK Platforms → Android 8.0+)

### Step 2: Import Project
1. Open Android Studio
2. **File → Open** → Select the `MYRA` folder
3. Wait for Gradle sync to complete (~2-3 minutes)
4. If Gradle sync fails: **File → Invalidate Caches → Restart**

### Step 3: Add Missing Icons (Required)
Create these vector drawable files in `app/src/main/res/drawable/`:

**ic_mic_on.xml**
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="36dp" android:height="36dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF1744"
        android:pathData="M12,14c1.66,0 3,-1.34 3,-3L15,5c0,-1.66-1.34,-3-3,-3S9,3.34 9,5l0,6c0,1.66 1.34,3 3,3zM17.3,11c0,3-2.54,5.1-5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21l2,0l0,-3.28c3.28,-0.49 6,-3.31 6,-6.72l-1.7,0z"/>
</vector>
```

**ic_mic_off.xml**
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="36dp" android:height="36dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#555555"
        android:pathData="M12,14c1.66,0 3,-1.34 3,-3L15,5c0,-1.66-1.34,-3-3,-3S9,3.34 9,5l0,6c0,1.66 1.34,3 3,3zM17.3,11c0,3-2.54,5.1-5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21l2,0l0,-3.28c3.28,-0.49 6,-3.31 6,-6.72l-1.7,0z"/>
</vector>
```

**ic_settings.xml**
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#888888"
        android:pathData="M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"/>
</vector>
```

**ic_close.xml**
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF1744"
        android:pathData="M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z"/>
</vector>
```

**ic_battery.xml**
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="16dp" android:height="16dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF6D6D"
        android:pathData="M15.67,4H14V2h-4v2H8.33C7.6,4 7,4.6 7,5.33v15.33C7,21.4 7.6,22 8.33,22h7.33c0.74,0 1.34,-0.6 1.34,-1.33V5.33C17,4.6 16.4,4 15.67,4z"/>
</vector>
```

**ic_myra_notif.xml** (notification icon - simple circle)
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF1744"
        android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z"/>
</vector>
```

### Step 4: Add Launcher Icons
- Right-click `res` → **New → Image Asset**
- Icon type: **Launcher Icons**
- Use any red/dark image as source
- Click **Finish**

### Step 5: Build & Run
```
Build → Build Bundle(s)/APK(s) → Build APK(s)
```
APK location: `app/build/outputs/apk/debug/app-debug.apk`

---

## 📱 First Launch Setup

### 1. Grant API Key
- Open MYRA app → tap ⚙️ Settings
- Enter your **Gemini API Key** from https://aistudio.google.com/app/apikey
- Enter your name

### 2. Enable Accessibility (REQUIRED for app control)
- Settings → Accessibility Status card → tap it
- Find **MYRA** in the list → Enable it
- Come back → green checkmark ✅ should appear

### 3. Enable Overlay Permission
- Settings → Apps → MYRA → Display over other apps → Allow

### 4. Set Prime Contact
- Settings → Prime Contact section
- Enter close friend's name + phone number
- Say: "MYRA, call my close friend" → it calls them instantly!

### 5. Set Personality
- GF Mode 💖 = Hinglish, emotional, caring
- Professional 💼 = Formal English
- Assistant 🤖 = Balanced

---

## 🎤 How to Use

### Voice Commands
| Say this | MYRA does this |
|----------|----------------|
| "YouTube kholo" | Opens YouTube |
| "WhatsApp band karo" | Closes WhatsApp |
| "Mere close friend ko call karo" | Calls prime contact |
| "Volume badhao" | Increases volume |
| "Torch on karo" | Turns on flashlight |
| "WiFi band karo" | Opens WiFi settings |
| "Mujhe yaad dilao kal 5 baje meeting" | (AI reminder reply) |

### Overlay Trigger
- **Double press power button** → MYRA orb appears on screen
- Tap orb → opens MYRA app
- Drag orb anywhere on screen

### Incoming Call Handling
1. Someone calls
2. MYRA automatically says: *"Sir, [Name] ka call aa raha hai... uthau ya reject karu?"*
3. Say **"Uthao"** → call accepted
4. Say **"Reject karo"** → call rejected
5. MYRA goes back to sleep after call decision

---

## 🏗️ Project Structure

```
MYRA/
├── app/src/main/
│   ├── java/com/myra/assistant/
│   │   ├── ai/
│   │   │   ├── GeminiClient.kt       ← Gemini REST API (NO WebSocket)
│   │   │   └── CommandParser.kt      ← Voice → Device command parser
│   │   ├── model/
│   │   │   └── AppCommand.kt         ← Command data model
│   │   ├── service/
│   │   │   ├── AccessibilityHelperService.kt  ← App open/close/UI
│   │   │   ├── CallMonitorService.kt          ← Incoming call detection
│   │   │   ├── MyraOverlayService.kt          ← Floating orb overlay
│   │   │   ├── PowerButtonReceiver.kt         ← Double power = overlay
│   │   │   └── BootReceiver.kt               ← Auto-start on reboot
│   │   ├── ui/
│   │   │   ├── main/
│   │   │   │   ├── MainActivity.kt      ← Main screen + speech
│   │   │   │   ├── OrbAnimationView.kt  ← Custom animated orb
│   │   │   │   └── UiComponents.kt      ← WaveformView, ChatAdapter
│   │   │   └── settings/
│   │   │       └── SettingsActivity.kt  ← Settings + Prime badge
│   │   └── viewmodel/
│   │       └── MainViewModel.kt         ← All phone actions
│   ├── res/
│   │   ├── layout/                      ← XML layouts
│   │   ├── drawable/                    ← Shapes, backgrounds
│   │   ├── values/                      ← Colors, strings, themes
│   │   └── xml/                         ← Accessibility config
│   └── AndroidManifest.xml
└── build.gradle
```

---

## ⚠️ Common Issues & Fixes

| Problem | Fix |
|---------|-----|
| Gradle sync fails | File → Invalidate Caches → Restart |
| "Missing resource" error | Add the icon XML files from Step 3 above |
| Mic not working | Grant RECORD_AUDIO permission in phone Settings → Apps → MYRA |
| App control not working | Enable Accessibility Service (Step 2) |
| Overlay not showing | Allow "Display over other apps" permission |
| API not responding | Check internet, verify API key in Settings |
| Call not detected | Grant READ_PHONE_STATE + ANSWER_PHONE_CALLS permissions |

---

## 🔑 Getting Your Gemini API Key

1. Go to https://aistudio.google.com/app/apikey
2. Click **Create API Key**
3. Copy the key (starts with `AIza...`)
4. Paste in MYRA Settings → API Key field

**Free tier**: 15 requests/minute, 1 million tokens/day — plenty for personal use!

---

## 🚀 Features Summary

- ✅ **Gemini 2.5 Flash** — Google's latest AI (REST API, no WebSocket)
- ✅ **GF Mode** — Hinglish, emotional personality
- ✅ **⭐ Prime Contact** — One-command call/message your special person
- ✅ **Incoming Call AI** — Auto announces caller, accepts/rejects by voice
- ✅ **Animated Orb** — Reacts to voice in real-time (speaking/thinking/listening)
- ✅ **Red Screen Effect** — Screen tints red when MYRA is active
- ✅ **Overlay Orb** — Floats on home screen like Google Assistant
- ✅ **Power Button Trigger** — Double press → MYRA activates
- ✅ **App Control** — Open/close any app via Accessibility Service
- ✅ **System Control** — Volume, flashlight, WiFi, Bluetooth
- ✅ **Call & SMS** — Make calls, send SMS, WhatsApp messages
- ✅ **Waveform Animation** — Bars animate to your voice amplitude
- ✅ **3 Personalities** — GF / Professional / Assistant mode
- ✅ **Chat History** — Scrollable conversation log

---

*Built with ❤️ — MYRA v1.0*
