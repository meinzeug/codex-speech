# Codex Speech

Native Android **viewer** + local backend for controlling the `codex` CLI over your LAN. The backend runs on your PC, the Android app streams a **real terminal** (no WebView) and supports push‑to‑talk speech‑to‑text with optional auto‑send. A separate Linux GUI app remains available in `apps/linux-gui-py`.

## What You Get
- **Android viewer** (Compose + Termux terminal-view)
- **FastAPI backend** with PTY‑based Codex session
- **faster‑whisper STT** on the backend (fast + configurable)
- **Two STT modes**: insert only or auto‑send
- **Directory manager** (browse, create, rename, delete)
- **Server profiles** + per‑session working directory

**Android App ID:** `com.meinzeug.codexspeech.viewer`

---

## Repo Layout
```
apps/
  android-viewer/   Native Android viewer
  backend/          FastAPI WebSocket + STT server
  linux-gui-py/     Original Linux GUI (GTK4 + VTE + Vosk)
```

---

## One‑Line Install (Backend + Android Viewer)
```
curl -fsSL https://raw.githubusercontent.com/meinzeug/codex-speech/main/install.sh | bash -s -- ~/codex-speech
```

This will:
- install system deps (Java, Node/PM2, Android SDK tools, ADB)
- set up the backend venv
- start backend via PM2
- build the Android APK
- install it to a connected device (interactive if multiple devices)

**Optional Linux GUI install:**
```
CODEX_SPEECH_INSTALL_GUI=1 \
  curl -fsSL https://raw.githubusercontent.com/meinzeug/codex-speech/main/install.sh | bash -s -- ~/codex-speech
```

You can override the repo URL:
```
CODEX_SPEECH_REPO=https://github.com/yourfork/codex-speech.git \
  curl -fsSL https://raw.githubusercontent.com/yourfork/codex-speech/main/install.sh | bash -s -- ~/codex-speech
```

---

## Android Viewer (apps/android-viewer)

### Build (manual)
If the Gradle binary is missing, run the one‑line installer (it downloads Gradle) or place Gradle 8.5 at `apps/android-viewer/gradle-8.5/`.
```
./apps/android-viewer/gradle-8.5/bin/gradle -p apps/android-viewer :app:assembleDebug
```

### Install to device
```
adb install -r apps/android-viewer/app/build/outputs/apk/debug/app-debug.apk
```

### Connect flow
1. Ensure PC and phone are on the same Wi‑Fi.
2. Find your PC IPv4:
```
ip -4 addr show | grep -E "inet .*"
# or
ip route get 1.1.1.1
```
3. Enter IP + port `8000` in the Android app.
4. Optionally set working directory (Browse → Directory Manager).
5. Tap **Connect**.

### STT modes
- **Record**: transcribe and insert into input (no auto‑send).
- **Mic**: transcribe and auto‑send to terminal.

---

## Backend (apps/backend)

### What it does
- `/ws`: WebSocket spawning Codex in a PTY
- `/stt`: audio → text (faster‑whisper)
- `/dirs`: list directory suggestions
- `/dirs/create`: create directory
- `/dirs/rename`: rename directory
- `/dirs/delete`: delete directory

### Run (manual)
```
python3 -m venv apps/backend/.venv
apps/backend/.venv/bin/pip install -r apps/backend/requirements.txt
apps/backend/.venv/bin/uvicorn main:app --host 0.0.0.0 --port 8000
```

### Run (PM2)
```
pm2 start ecosystem.config.js --only codex-backend
pm2 logs codex-backend
```

### STT configuration
Environment variables:
- `STT_MODEL` (default `small`)
- `STT_DEVICE` (default `cpu`)
- `STT_COMPUTE_TYPE` (default `int8`)

Example:
```
STT_MODEL=medium STT_DEVICE=cuda STT_COMPUTE_TYPE=int8_float16 pm2 restart codex-backend --update-env
```

---

## Linux GUI (apps/linux-gui-py)

The original desktop GUI is preserved and can be run independently.

```
cd apps/linux-gui-py
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python3 src/main.py
```

---

## Firewall
Allow inbound TCP 8000 on your LAN (UFW example):
```
sudo ufw allow 8000/tcp
# or restrict to LAN
sudo ufw allow from 192.168.0.0/16 to any port 8000 proto tcp
```

---

## Troubleshooting
- **App can’t connect**: check IP, firewall, backend listening on `0.0.0.0:8000`.
- **ADB device missing**: enable USB debugging, run `adb devices`, authorize PC.
- **Codex not found**: set `CODEX_PATH` or `CODEX_CMD` in backend env.

---

## License
MIT
