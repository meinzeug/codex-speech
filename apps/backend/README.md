# Codex Mobile Backend

WebSocket backend that spawns the local `codex` CLI in a PTY and streams I/O to the Android app.

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

## Android App Connection

1. Put phone and PC on the same Wi‑Fi.
2. Find your PC IPv4:

```
ip -4 addr show | grep -E "inet .*" 
# or
ip route get 1.1.1.1
```

3. In the Android app, enter that IP and port `8000`, then tap **Connect**.

## Config and Environment Overrides

By default, the backend reads Codex settings from:

`~/.config/codex-stt-assistant/config.json`

Overrides:

- `CODEX_CMD` full command string (highest priority)
- `CODEX_PATH` path or name of codex binary
- `CODEX_ARGS` args string
- `CODEX_WORKDIR` working directory
- `CODEX_ALLOW_SHELL_FALLBACK=1` fallback to `/bin/bash` if codex not found
