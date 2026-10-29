# Yara AI – Ultimate (rebuilt from scratch)

Native Android app (Kotlin + Jetpack Compose). Your uploaded zip only had a compiled `app-debug.apk`, so the source was rebuilt.

## Build the APK (no PC needed)
1. Create a new GitHub repo → upload **everything inside this folder** (keep the `.github` folder).
2. Open the **Actions** tab → **Build Yara APK** → *Run workflow* (it also runs on every push).
3. When it turns green, open the run → **Artifacts → Yara-AI-APK** → download, unzip, install `app-debug.apk`.
4. If the build fails, open the failed step and paste the red error text to Claude – it is a one-line fix.

## First run
Settings ⚙️ → add 1–4 free keys (Gemini from aistudio.google.com, Groq from console.groq.com). Allow microphone + notifications.

## What's inside
- **WhatsApp-style home**: Chats / Status / Tools / Calls; four Yara chats (Girlfriend, Career Coach, Data Analyst, Assistant), ticks, typing indicator, image attach, voice mic.
- **4-key API pool**: Auto mode balances by least-used-today; Manual mode uses your chosen main key first. Any key hitting a limit/error is paused and Yara fails over silently.
- **Offline memory brain**: every good answer is saved on the phone (SQLite). When all keys are exhausted/offline, Yara retrieves from memory, then falls back to emotional rule-based chat (English / Malayalam / mixed). *Study mode* (Tools) pre-downloads ~200 Q&As on 10 topics using spare quota; also learns facts about you (name, likes, work).
- **Emotions**: mood tag per reply → animated 2D anime avatar (blink, breathing, talking mouth, brows, blush), emoji, haptics, and a mood-matched voice (pitch/speed) with vocal emotes ("Hehe!", "Aww…").
- **Mixed-language voice**: Malayalam and English segments are spoken with the matching TTS voice. Install the Malayalam voice in *Google Speech Services* if she is silent on Malayalam.
- **"Hey Yara" / "Bye Yara"**: foreground wake-word service. Voice commands: open apps, torch, time/date, reminders ("remind me to X in 10 minutes / at 6 pm"), Google/YouTube search, "find jobs for data analyst in Kochi", "look at my screen", "stop".
- **Screen vision**: Accessibility service reads on-screen text + takes a screenshot (Android 11+) → Gemini analyses and Yara answers aloud. Notification button "Look at screen" works from any app.
- **Career**: mock interviews with scoring, AI CV writer → PDF export, job finder (LinkedIn/Naukri/Indeed deep links + AI role matching), data analytics (CSV stats on-device + AI insights), reminders.

## Honest limits
- LinkedIn has no public API for personal apps → paste profile text + open LinkedIn search links instead.
- No 3D / 4K video emotes (APK size + GitHub build limits) → vector 2D avatar + synthesized emotes.
- Android's recognizer can't listen while Yara speaks (echo) → she pauses, speaks, then listens again. Continuous listening uses battery; set battery to "Unrestricted".
- Explicit/sexual content is not supported; Yara is flirty and romantic but tasteful.
- Default models: `gemini-2.0-flash`, `llama-3.3-70b-versatile`. Change per key in Settings if Google/Groq rename them.
