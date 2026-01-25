# Codex STT Assistant

Speech-to-text enabled GUI for Codex CLI on Linux.

## Features
- Voice-controlled Codex interaction (offline Vosk)
- GTK4 UI with VTE terminal emulation
- Dark and light themes
- Input history and keyboard shortcuts
- Configurable settings and autosubmit

## Quick Start
1. Install system deps and Python packages:
   - `./install.sh`
2. Run the app:
   - `codex-stt-assistant`

## Requirements
- Ubuntu 22.04+ (recommended)
- Python 3.9+
- GTK4 + VTE (libvte-2.91-gtk4)
- PortAudio (for microphone capture)

## Configuration
Config file: `~/.config/codex-stt-assistant/config.json`

## Development
- Create venv: `python3 -m venv venv && source venv/bin/activate`
- Install deps: `pip install -r requirements.txt`
- Run: `python3 src/main.py`
- Tests: `pytest tests/ -v`
- Headless self-test: `scripts/run_self_test.sh`

## License
MIT
