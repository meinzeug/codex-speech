# Installation

## Ubuntu/Debian
1. Install system dependencies:
   - `sudo apt-get update`
   - `sudo apt-get install -y python3-dev python3-pip python3-venv libgtk-4-dev libvte-2.91-gtk4-0 libvte-2.91-gtk4-dev gir1.2-vte-3.91 libgirepository1.0-dev portaudio19-dev libasound2-dev pulseaudio gstreamer1.0-tools gstreamer1.0-plugins-base gstreamer1.0-plugins-good build-essential pkg-config`
2. Create venv and install Python deps:
   - `cd apps/linux-gui-py`
   - `python3 -m venv venv`
   - `source venv/bin/activate`
   - `pip install -r requirements.txt`
3. Run:
   - `python3 src/main.py`

## Optional model download
Download a Vosk model and unpack into:
- `~/.local/share/codex-stt-assistant/models/`

Example model:
- `vosk-model-de-0.21`
