#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
echo "=== Codex STT Assistant Installer ==="
echo ""

if ! grep -q "Ubuntu" /etc/os-release; then
  echo "Warning: This script is optimized for Ubuntu. Proceed? (y/n)"
  read -r response
  if [[ ! "$response" =~ ^[Yy]$ ]]; then
    exit 1
  fi
fi

echo "Installing system dependencies..."
sudo apt-get update
sudo apt-get install -y \
  python3-dev python3-pip python3-venv \
  libgtk-4-dev libvte-2.91-gtk4-0 libvte-2.91-gtk4-dev gir1.2-vte-3.91 libgirepository1.0-dev \
  libgirepository-2.0-dev \
  portaudio19-dev libasound2-dev pulseaudio \
  gstreamer1.0-tools gstreamer1.0-plugins-base gstreamer1.0-plugins-good \
  build-essential pkg-config unzip wget xvfb

echo "Creating virtual environment..."
python3 -m venv venv
source venv/bin/activate

echo "Installing Python packages..."
pip install --upgrade pip
pip install -r requirements.txt

echo "Creating application directories..."
mkdir -p ~/.local/share/codex-stt-assistant/models
mkdir -p ~/.config/codex-stt-assistant

echo "Downloading German Vosk model (this may take a few minutes)..."
cd ~/.local/share/codex-stt-assistant/models
wget -q --show-progress https://alphacephei.com/vosk/models/vosk-model-de-0.21.zip
unzip -q vosk-model-de-0.21.zip
rm vosk-model-de-0.21.zip
cd -

echo "Installing desktop entry..."
sudo cp data/codex-stt-assistant.desktop /usr/share/applications/
sudo update-desktop-database

echo "Creating launcher script..."
cat > ~/.local/bin/codex-stt-assistant << EOS
#!/bin/bash
APP_DIR="$SCRIPT_DIR"
source "$APP_DIR/venv/bin/activate"
export PYTHONDONTWRITEBYTECODE=1
python3 -B "$APP_DIR/src/main.py" "$@"
EOS
chmod +x ~/.local/bin/codex-stt-assistant

echo ""
echo "=== Installation complete! ==="
echo "Run 'codex-stt-assistant' or find it in your application menu."
echo ""
