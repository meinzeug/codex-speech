# Codex Speech Backend

WebSocket backend that spawns the local `codex` CLI in a PTY and streams I/O to the Android viewer app.

## Setup

```
python3 -m venv apps/backend/.venv
apps/backend/.venv/bin/pip install -r apps/backend/requirements.txt
```

## Run (dev)

```
apps/backend/.venv/bin/uvicorn main:app --host 0.0.0.0 --port 8000
```

Health check:

```
curl http://127.0.0.1:8000/health
```

## Run with PM2

```
pm2 start ecosystem.config.js --only codex-backend
pm2 list
pm2 logs codex-backend
```

## Firewall (Linux / UFW)

Allow inbound TCP on port 8000 (IPv4 and IPv6):

```
sudo ufw allow 8000/tcp
sudo ufw status verbose
```

If you want to restrict to your LAN only, replace with the correct subnet, for example:

```
sudo ufw allow from 192.168.0.0/16 to any port 8000 proto tcp
```

## Android Viewer Connection

1. Put phone and PC on the same Wi‑Fi.
2. Find your PC IPv4:

```
ip -4 addr show | grep -E "inet .*" 
# or
ip route get 1.1.1.1
```

3. In the Android app, enter that IP and port `8000`, then tap **Connect**.

## Runner (React Native / Flutter)

The backend can start hot‑reload sessions for React Native or Flutter projects in the current working directory.

Endpoints:

- `GET /runner/detect` – detect project type (`react-native` or `flutter`)
- `GET /runner/devices` – list adb devices
- `GET /runner/scan` – scan a base directory (depth configurable)
- `POST /runner/start` – start Metro + install React Native app, or `flutter run`
- `POST /runner/stop` – stop runner processes
- `POST /runner/reload` – Flutter hot reload (`type=hot`) or hot restart (`type=restart`)
- `POST /runner/devmenu` – open React Native Dev Menu on device
- `POST /runner/rn/host` – set React Native debug server host (best-effort)
- `POST /runner/rn/reload` – trigger RN JS reload

Notes:

- React Native uses `adb reverse` on port `8081` when in ADB mode.
- Flutter hot reload is triggered by sending `r` to the `flutter run` process.

## Config and Environment Overrides

By default, the backend reads Codex settings from:

`~/.config/codex-stt-assistant/config.json`

Overrides:

- `CODEX_CMD` full command string (highest priority)
- `CODEX_PATH` path or name of codex binary
- `CODEX_ARGS` args string
- `CODEX_WORKDIR` working directory
- `CODEX_ALLOW_SHELL_FALLBACK=1` fallback to `/bin/bash` if codex not found
