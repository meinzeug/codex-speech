# Codex STT Assistant

Speech-to-text enabled GUI for Codex CLI on Linux, plus a native Android client that can connect to a local backend over IPv4 and drive Codex remotely.

## Features
- Voice-controlled Codex interaction (desktop app, offline Vosk)
- GTK4 UI with VTE terminal emulation (desktop)
- Native Android app with real terminal emulator (no WebView)
- Android STT with two modes (manual insert / auto-send)
- Backend WebSocket server with PTY for Codex CLI
- Faster-Whisper STT on the backend
- Server profiles, working-directory override per session
- Auto-fit terminal font, pinch zoom, landscape-safe layout

## Repository Layout
```
apps/
  backend/   FastAPI WebSocket + STT server
  android/   Native Android client (Compose + Termux terminal)
```

## Backend (apps/backend)

### What it does
- `/ws`: WebSocket that spawns Codex in a PTY and streams terminal I/O
- `/stt`: Audio transcription via faster-whisper
- `/health`: health probe

### Install
```
python3 -m venv apps/backend/.venv
apps/backend/.venv/bin/pip install -r apps/backend/requirements.txt
```

### Run (dev)
```
apps/backend/.venv/bin/uvicorn main:app --host 0.0.0.0 --port 8000
```

### Run (pm2)
```
pm2 start ecosystem.config.js --only codex-backend
```

### Configuration
- Default config file: `~/.config/codex-stt-assistant/config.json`
- The backend will try to resolve `codex` from:
  - `CODEX_CMD` / `CODEX_PATH`
  - `codex_path` in config
  - `PATH` (including `~/.nvm/.../bin/codex`)

#### Working directory per session
The Android app passes the directory via query param:
```
ws://<host>:8000/ws?cwd=/path/to/workdir
```
If invalid, backend returns an error.

#### STT (faster-whisper)
Environment variables:
- `STT_MODEL` (default: `small`)
- `STT_DEVICE` (default: `cpu`)
- `STT_COMPUTE_TYPE` (default: `int8`)

Example:
```
STT_MODEL=medium STT_DEVICE=cuda STT_COMPUTE_TYPE=int8_float16 pm2 restart codex-backend --update-env
```

## Android App (apps/android)

### What it does
- Native terminal renderer (Termux terminal-view)
- Pinch zoom + `A- / A+` controls
- Auto-fit font (`Fit`)
- Landscape-safe layout (two columns)
- Server profiles and working-directory override
- STT record (manual) and mic (auto-send)

### Build prerequisites
- Android SDK + build-tools
- NDK 27.1.12297006
- JDK 17+ recommended

### Build
```
./apps/android/gradle-8.5/bin/gradle -p apps/android :app:assembleDebug
```

### Install to device
```
adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk
```

### Connect flow
1. Ensure backend is reachable on your LAN IP (firewall allows port 8000).
2. Select a server from dropdown (or Custom), set IP/Port.
3. Optional: set working directory via the gear icon.
4. Tap **Connect**.

### Server profiles
- Use the **Server** dropdown to pick or manage servers.
- **Manage servers…** opens CRUD dialog.
- Each server stores: name, host, port, working directory.

### STT usage
- **Record** button: record → transcribe → insert into input (no auto-send).
- **Mic** button: record → transcribe → auto-send to Codex.

## Desktop App (original)

### Quick Start
1. Install system deps and Python packages:
   - `./install.sh`
2. Run the app:
   - `codex-stt-assistant`

### Requirements
- Ubuntu 22.04+ (recommended)
- Python 3.9+
- GTK4 + VTE (libvte-2.91-gtk4)
- PortAudio (for microphone capture)

### Configuration
Config file: `~/.config/codex-stt-assistant/config.json`

## Development
- Create venv: `python3 -m venv venv && source venv/bin/activate`
- Install deps: `pip install -r requirements.txt`
- Run: `python3 src/main.py`
- Tests: `pytest tests/ -v`
- Headless self-test: `scripts/run_self_test.sh`

## License
MIT
