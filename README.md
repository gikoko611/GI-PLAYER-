# GI PLAYER PRO ⚡

🏆 **POWERED BY I KO KO**

Modern Cyber Theme Video Player — MX Player features + PRO toolkit, built with Kotlin + Media3 (ExoPlayer).

## ✨ Features

- 🎬 Modern Cyber UI (neon cyan + gold 3D GI logo)
- 🚀 Cyber 3D splash animation — rotating rings, pulsing gold glow, 3D GI logo flip + **POWERED BY I KO KO**
- ▶️ All-format playback engine (Media3/ExoPlayer)
- 👆 MX-style gestures — swipe left = brightness, right = volume, horizontal = seek, double-tap ±10s / play-pause
- 🎚 Equalizer presets + BassBoost + Volume Boost (PRO)
- 🌐 Network streams (m3u8 / mp4 / HLS)
- 💬 Subtitles — built-in track selection + external SRT loader
- 🔁 Loop modes + speed 0.25x – 4x
- 📺 Picture-in-Picture (auto when leaving while playing)
- 🌙 Night mode + zoom modes (FIT / CROP)
- ⏱ Sleep timer
- 📁 Folder library + sorting (date / name / size)
- 🎵 Background playback with notification controls
- ⏯ Auto-resume last playback position

## 🤖 Build APK (GitHub Actions — automatic)

Push တိုင်း APK build အလိုအလျောက် စတင်ပါမည်။

1. Repo၏ **Actions** tab သို့ သွားပါ → **Build GI PLAYER PRO APK** workflow
2. Run အပြီးတွင် run အောက်ရှိ **Artifacts** ထဲမှ **GI-PLAYER-PRO-APK** ကို download လုပ်ပါ
3. သို့မဟုတ် repo၏ **Releases** စာမျက်ခြံတွင် **v1.0.0** အောက်မှ **GI-PLAYER-PRO.apk** ကို တိုက်ရိုက် download နိုင်ပါသည်

APK file name: **GI-PLAYER-PRO.apk** (debug-signed, install-ready)

## 🔧 Local build

Requirements: JDK 17, Gradle 8.7+

```bash
gradle wrapper          # generate wrapper (first time)
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

---

⚡ GI PLAYER PRO — 🏆 POWERED BY I KO KO
