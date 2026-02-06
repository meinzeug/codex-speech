#!/usr/bin/env bash
set -euo pipefail

REPO_URL="${CODEX_SPEECH_REPO:-https://github.com/meinzeug/codex-speech.git}"
TARGET_DIR="${1:-$HOME/codex-speech}"
INSTALL_GUI="${CODEX_SPEECH_INSTALL_GUI:-0}"
export DEBIAN_FRONTEND=noninteractive

print() {
  echo -e "$*"
}

if [[ -d "./apps/backend" && -d "./apps/android-viewer" && -d "./apps/linux-gui-py" ]]; then
  REPO_DIR="$(pwd)"
else
  if [[ -e "$TARGET_DIR" && ! -d "$TARGET_DIR/.git" ]]; then
    print "Target path exists but is not a git repo: $TARGET_DIR"
    print "Choose an empty directory or an existing codex-speech clone."
    exit 1
  fi
  if [[ -d "$TARGET_DIR/.git" ]]; then
    print "Updating repo in $TARGET_DIR"
    git -C "$TARGET_DIR" pull --ff-only
  else
    print "Cloning $REPO_URL -> $TARGET_DIR"
    git clone "$REPO_URL" "$TARGET_DIR"
  fi
  REPO_DIR="$TARGET_DIR"
fi

SUDO="sudo"
if [[ "$(id -u)" == "0" ]]; then
  SUDO=""
fi

print "\n=== Installing system dependencies ==="
if ! $SUDO apt-get update; then
  print "apt-get update failed. Continuing with install attempt..."
fi

apt_install() {
  if $SUDO apt-get install -y "$@"; then
    return 0
  fi
  print "apt-get install failed. Attempting to fix broken packages..."
  $SUDO apt-get -f install -y || true
  $SUDO apt-get install -y "$@"
}

apt_install \
  git curl unzip zip \
  python3 python3-venv python3-pip \
  build-essential ffmpeg \
  openjdk-17-jdk \
  nodejs \
  android-sdk-platform-tools

ensure_npm_or_pnpm() {
  if command -v npm >/dev/null 2>&1; then
    return 0
  fi

  print "\n=== npm not found, attempting install ==="
  if $SUDO apt-get install -y npm; then
    return 0
  fi

  print "npm install failed. Trying corepack/pnpm fallback..."
  if command -v corepack >/dev/null 2>&1; then
    corepack enable >/dev/null 2>&1 || true
    corepack prepare pnpm@latest --activate >/dev/null 2>&1 || true
    if command -v pnpm >/dev/null 2>&1; then
      return 0
    fi
  fi

  print "ERROR: npm or pnpm not available. Please resolve Node.js toolchain and rerun."
  exit 1
}

ensure_npm_or_pnpm

if ! command -v pm2 >/dev/null 2>&1; then
  print "\n=== Installing PM2 ==="
  if command -v npm >/dev/null 2>&1; then
    $SUDO npm install -g pm2
  else
    $SUDO pnpm add -g pm2
  fi
fi

if [[ "$INSTALL_GUI" == "1" ]]; then
  print "\n=== Installing Linux GUI dependencies ==="
  $SUDO apt-get install -y \
    python3-dev \
    libgtk-4-dev libvte-2.91-gtk4-0 libvte-2.91-gtk4-dev \
    gir1.2-vte-3.91 libgirepository1.0-dev libgirepository-2.0-dev \
    portaudio19-dev libasound2-dev pulseaudio \
    gstreamer1.0-tools gstreamer1.0-plugins-base gstreamer1.0-plugins-good \
    pkg-config xvfb
fi

print "\n=== Setting up Android SDK ==="
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/.android-sdk}"
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

if [[ ! -x "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
  TMP_DIR="$(mktemp -d)"
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  print "Downloading Android command line tools..."
  curl -fsSL -o "$TMP_DIR/cmdline-tools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  unzip -q "$TMP_DIR/cmdline-tools.zip" -d "$TMP_DIR"
  mv "$TMP_DIR/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  rm -rf "$TMP_DIR"
fi

yes | sdkmanager --licenses >/dev/null
sdkmanager \
  "platform-tools" \
  "platforms;android-34" \
  "build-tools;34.0.0" \
  "ndk;27.1.12297006" >/dev/null

if command -v java >/dev/null 2>&1; then
  JAVA_BIN="$(readlink -f "$(command -v java)")"
  export JAVA_HOME="$(dirname "$(dirname "$JAVA_BIN")")"
fi

print "\n=== Ensuring local Gradle ==="
GRADLE_VERSION="8.5"
GRADLE_DIR="$REPO_DIR/apps/android-viewer/gradle-$GRADLE_VERSION"
if [[ ! -x "$GRADLE_DIR/bin/gradle" ]]; then
  TMP_DIR="$(mktemp -d)"
  GRADLE_ZIP="gradle-${GRADLE_VERSION}-bin.zip"
  print "Downloading Gradle $GRADLE_VERSION..."
  curl -fsSL -o "$TMP_DIR/$GRADLE_ZIP" "https://services.gradle.org/distributions/$GRADLE_ZIP"
  unzip -q "$TMP_DIR/$GRADLE_ZIP" -d "$REPO_DIR/apps/android-viewer"
  rm -rf "$TMP_DIR"
fi

print "\n=== Setting up backend ==="
python3 -m venv "$REPO_DIR/apps/backend/.venv"
"$REPO_DIR/apps/backend/.venv/bin/pip" install --upgrade pip
"$REPO_DIR/apps/backend/.venv/bin/pip" install -r "$REPO_DIR/apps/backend/requirements.txt"

print "\n=== Starting backend via PM2 ==="
( cd "$REPO_DIR" && pm2 start ecosystem.config.js --only codex-backend ) || pm2 restart codex-backend

if [[ "$INSTALL_GUI" == "1" ]]; then
  print "\n=== Setting up Linux GUI app ==="
  python3 -m venv "$REPO_DIR/apps/linux-gui-py/venv"
  "$REPO_DIR/apps/linux-gui-py/venv/bin/pip" install --upgrade pip
  "$REPO_DIR/apps/linux-gui-py/venv/bin/pip" install -r "$REPO_DIR/apps/linux-gui-py/requirements.txt"
  mkdir -p "$HOME/.local/bin"
  cat > "$HOME/.local/bin/codex-speech-gui" << EOS
#!/usr/bin/env bash
source "$REPO_DIR/apps/linux-gui-py/venv/bin/activate"
export PYTHONDONTWRITEBYTECODE=1
python3 -B "$REPO_DIR/apps/linux-gui-py/src/main.py" "\$@"
EOS
  chmod +x "$HOME/.local/bin/codex-speech-gui"
  print "GUI launcher installed: codex-speech-gui"
fi

print "\n=== Building Android APK ==="
chmod +x "$REPO_DIR/apps/android-viewer/gradle-8.5/bin/gradle"
"$REPO_DIR/apps/android-viewer/gradle-8.5/bin/gradle" -p "$REPO_DIR/apps/android-viewer" :app:assembleDebug
APK="$REPO_DIR/apps/android-viewer/app/build/outputs/apk/debug/app-debug.apk"

print "\n=== Installing APK to device (optional) ==="
if command -v adb >/dev/null 2>&1; then
  mapfile -t DEVICES < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  if [[ ${#DEVICES[@]} -eq 1 ]]; then
    print "Installing to ${DEVICES[0]}"
    adb -s "${DEVICES[0]}" install -r "$APK"
  elif [[ ${#DEVICES[@]} -gt 1 ]]; then
    print "Select device to install APK:"
    select SERIAL in "${DEVICES[@]}" "Skip"; do
      if [[ "$SERIAL" == "Skip" ]]; then
        break
      fi
      if [[ -n "$SERIAL" ]]; then
        adb -s "$SERIAL" install -r "$APK"
        break
      fi
    done
  else
    print "No Android devices detected. APK built at: $APK"
  fi
else
  print "adb not found. APK built at: $APK"
fi

print "\n=== Done ==="
print "Backend: http://<this-machine-ip>:8000"
print "Android APK: $APK"
print "Open Codex Speech on your phone and connect to your LAN IP."
