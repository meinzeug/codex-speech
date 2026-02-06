<p align="center">
  <img src="docs/hero.svg" alt="Codex Speech" width="100%">
</p>

<p align="center">
  <img src="docs/logo-android-viewer.png" alt="Codex Speech Viewer" width="160">
</p>

<h1 align="center">Codex Speech</h1>
<p align="center">Native Android viewer + local backend for controlling the Codex CLI over your LAN or VPN.</p>

<p align="center">
  <a href="https://github.com/meinzeug/codex-speech/releases"><img alt="Release" src="https://img.shields.io/github/v/release/meinzeug/codex-speech?display_name=tag&style=for-the-badge"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/github/license/meinzeug/codex-speech?style=for-the-badge"></a>
  <a href="#android-viewer"><img alt="Android" src="https://img.shields.io/badge/Android-minSdk%2026-3DDC84?logo=android&logoColor=white&style=for-the-badge"></a>
  <a href="#backend"><img alt="Backend" src="https://img.shields.io/badge/Backend-FastAPI-009688?logo=fastapi&logoColor=white&style=for-the-badge"></a>
  <a href="#linux-gui"><img alt="Linux GUI" src="https://img.shields.io/badge/Linux-GTK4%20VTE-444?style=for-the-badge"></a>
</p>

<p align="center">
  <a href="#quick-start">Quick Start</a> |
  <a href="#android-viewer">Android Viewer</a> |
  <a href="#app-dev-hot-reload">App Dev Hot Reload</a> |
  <a href="#backend">Backend</a> |
  <a href="#troubleshooting">Troubleshooting</a>
</p>

---

## Overview
Codex Speech runs the Codex CLI locally on your PC and exposes a secure, local-first control surface to a **native Android app** over IPv4. The viewer streams a real terminal (no WebView) and adds push‑to‑talk speech‑to‑text. Development helpers for React Native and Flutter are built into the viewer UI.

**Android App ID:** `com.meinzeug.codexspeech.viewer`

---

## Highlights
- Native Android viewer (Compose + Termux terminal-view)
- FastAPI backend with PTY‑attached Codex session
- Faster‑whisper STT with insert‑only and auto‑send modes
- Directory manager with create/rename/delete
- Server profiles and per‑session working directory
- One‑line installer for backend + Android APK
- React Native / Flutter dev helpers (Metro + `flutter run`)
- Android MCP auto‑registered for device automation

---

## Architecture
<p align="center">
  <img src="docs/architecture.svg" alt="Codex Speech architecture" width="100%">
</p>

```mermaid
flowchart LR
  A[Android Viewer] <-->|LAN / IPv4| B[Backend: FastAPI + PTY]
  B -->|stdin/stdout| C[Codex CLI]
  A -->|audio| B
  B -->|transcript| A
```

---

## Repo Layout
```
apps/
  android-viewer/   Native Android viewer
  backend/          FastAPI WebSocket + STT server
  linux-gui-py/     Original Linux GUI (GTK4 + VTE + Vosk)
```

---

## Quick Start
One-line install (backend + Android viewer). The installer opens a TUI when a TTY is available.
```
curl -fsSL https://raw.githubusercontent.com/meinzeug/codex-speech/main/install.sh | bash -s -- ~/codex-speech
```

What it does:
- Installs system deps (Java, Node/PM2, Android SDK tools, ADB)
- Sets up backend venv and starts it via PM2
- Builds the Android viewer APK
- Installs APK to a connected device

Optional Linux GUI install:
```
CODEX_SPEECH_INSTALL_GUI=1 \
  curl -fsSL https://raw.githubusercontent.com/meinzeug/codex-speech/main/install.sh | bash -s -- ~/codex-speech
```

Install only the APK:
```
CODEX_SPEECH_COMPONENTS=android \
  curl -fsSL https://raw.githubusercontent.com/meinzeug/codex-speech/main/install.sh | bash -s -- ~/codex-speech
```

---

## Android Viewer
Build manually:
```
./apps/android-viewer/gradle-8.5/bin/gradle -p apps/android-viewer :app:assembleDebug
```

Install to device:
```
adb install -r apps/android-viewer/app/build/outputs/apk/debug/app-debug.apk
```

Connect flow:
1. Ensure PC and phone are on the same Wi‑Fi or VPN.
2. Find your PC IPv4 address.
3. Enter IP + port `8000` in the Android app.
4. Optional: select a working directory.
5. Tap **Connect**.

STT modes:
- **Record**: transcribe and insert into input, no auto‑send.
- **Mic**: transcribe and auto‑send to terminal.

---

## App Dev Hot Reload
The viewer UI includes a **Dev** section for React Native and Flutter projects in the current working directory.

Flow:
1. Detect a project and device.
2. **Start** builds/installs via ADB and opens the app.
3. Keep Metro/Flutter running for hot reload.

Notes:
- **RN LAN/VPN mode**: set Debug Host to your PC IP + Metro port (the UI can set it automatically).
- **Flutter hot reload** requires **USB or Wireless ADB**.
- Without ADB you can keep Metro running and reload JS, but cannot install or hot‑reload Flutter.

---

## Backend
Manual run:
```
python3 -m venv apps/backend/.venv
apps/backend/.venv/bin/pip install -r apps/backend/requirements.txt
apps/backend/.venv/bin/uvicorn main:app --host 0.0.0.0 --port 8000
```

PM2 run:
```
pm2 start ecosystem.config.js --only codex-backend
pm2 logs codex-backend
```

Port configuration:
- Default backend port is `8000`.
- Override with `CODEX_BACKEND_PORT=9000 pm2 restart codex-backend --update-env`

Settings UI:
- Open `http://<backend-ip>:<port>/settings` to edit Codex path, args, working directory and STT defaults.

STT configuration:
- `STT_MODEL` (default `small`)
- `STT_DEVICE` (default `cpu`)
- `STT_COMPUTE_TYPE` (default `int8`)

Example:
```
STT_MODEL=medium STT_DEVICE=cuda STT_COMPUTE_TYPE=int8_float16 pm2 restart codex-backend --update-env
```

---

## MCP (Android Automation)
The installer registers **the-android-mcp** in `~/.codex/config.toml`. This lets Codex control the connected Android device (screenshots, taps, input) via MCP.

---

## Linux GUI
The original desktop app is preserved.
```
cd apps/linux-gui-py
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python3 src/main.py
```

---

## Firewall
Allow inbound TCP on your backend port (UFW example):
```
sudo ufw allow 8000/tcp
sudo ufw allow from 192.168.0.0/16 to any port 8000 proto tcp
```

---

## Troubleshooting
- App can't connect: check IP, firewall, backend listens on the configured port (default `0.0.0.0:8000`).
- ADB device missing: enable USB debugging and authorize the PC.
- Codex not found: set `CODEX_PATH` or `CODEX_CMD` in backend env.
