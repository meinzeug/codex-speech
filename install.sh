#!/usr/bin/env bash
set -euo pipefail

REPO_URL="${CODEX_SPEECH_REPO:-https://github.com/meinzeug/codex-speech.git}"
TARGET_DIR_ARG="${1:-}"
NONINTERACTIVE="${CODEX_SPEECH_NONINTERACTIVE:-0}"
FORCE_TUI="${CODEX_SPEECH_TUI:-1}"
DEFAULT_GUI="${CODEX_SPEECH_INSTALL_GUI:-0}"
DEBUG="${CODEX_SPEECH_DEBUG:-0}"
LOG_FILE="${CODEX_SPEECH_LOG:-/tmp/codex-speech-install.log}"
BACKEND_PORT_DEFAULT="${CODEX_BACKEND_PORT:-8000}"
BACKEND_PORT="$BACKEND_PORT_DEFAULT"
export DEBIAN_FRONTEND=noninteractive

SUDO="sudo"
if [[ "$(id -u)" == "0" ]]; then
  SUDO=""
fi

if [[ "$DEBUG" == "1" ]]; then
  exec 3>>"$LOG_FILE"
fi

log() {
  if [[ "$DEBUG" == "1" ]]; then
    printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >&3
  fi
}

print() {
  echo -e "$*"
  log "$*"
}

TTY_IN=""
if [[ -r /dev/tty ]]; then
  TTY_IN="/dev/tty"
fi

USE_TUI=0
if [[ "$NONINTERACTIVE" != "1" && "$FORCE_TUI" != "0" && -n "$TTY_IN" ]]; then
  USE_TUI=1
fi

ensure_whiptail() {
  if command -v whiptail >/dev/null 2>&1; then
    return 0
  fi
  print "\n=== Installing TUI dependencies (whiptail) ==="
  if ! $SUDO apt-get update; then
    print "apt-get update failed. Continuing..."
  fi
  if ! $SUDO apt-get install -y whiptail; then
    print "Could not install whiptail. Falling back to text prompts."
    USE_TUI=0
    return 1
  fi
  return 0
}

if [[ "$USE_TUI" == "1" ]]; then
  if [[ -z "${TERM:-}" || "${TERM:-}" == "dumb" ]]; then
    export TERM="xterm-256color"
  fi
fi

tui_input() {
  whiptail --title "Codex Speech Installer" --backtitle "Codex Speech" \
    --inputbox "$1" 10 72 "$2" 3>&1 1>&2 2>&3
}

tui_menu() {
  local prompt="$1"
  shift
  whiptail --title "Codex Speech Installer" --backtitle "Codex Speech" \
    --menu "$prompt" 16 78 8 "$@" 3>&1 1>&2 2>&3
}

tui_checklist() {
  local prompt="$1"
  shift
  whiptail --title "Codex Speech Installer" --backtitle "Codex Speech" \
    --checklist "$prompt" 16 78 6 "$@" 3>&1 1>&2 2>&3
}

tui_yesno() {
  whiptail --title "Codex Speech Installer" --backtitle "Codex Speech" \
    --yesno "$1" 12 72
}

prompt() {
  local question="$1"
  local default="$2"
  if [[ "$NONINTERACTIVE" == "1" || -z "$TTY_IN" ]]; then
    echo "$default"
    return 0
  fi
  local answer=""
  read -r -p "$question [$default]: " answer < "$TTY_IN"
  if [[ -z "$answer" ]]; then
    echo "$default"
  else
    echo "$answer"
  fi
}

ensure_android_mcp() {
  local config_dir="$HOME/.codex"
  local config_file="$config_dir/config.toml"
  mkdir -p "$config_dir"
  if [[ -f "$config_file" ]] && grep -q "\[mcp_servers.the-android-mcp\]" "$config_file"; then
    return 0
  fi
  cat >> "$config_file" << 'EOF'

[mcp_servers.the-android-mcp]
command = "npx"
args = ["-y", "the-android-mcp"]
EOF
  print "Added the-android-mcp to ~/.codex/config.toml"
}

confirm() {
  local question="$1"
  local default="$2"
  local hint
  if [[ "$default" == "y" ]]; then
    hint="Y/n"
  else
    hint="y/N"
  fi
  local answer
  answer="$(prompt "$question ($hint)" "$default")"
  [[ "$answer" =~ ^[Yy]$ ]]
}

if [[ "$USE_TUI" == "1" ]]; then
  ensure_whiptail || true
fi

DEFAULT_DIR="${CODEX_SPEECH_TARGET_DIR:-${TARGET_DIR_ARG:-$HOME/codex-speech}}"
log "Default dir: $DEFAULT_DIR"
if [[ "$USE_TUI" == "1" ]]; then
  if ! TARGET_DIR="$(tui_input "Install directory" "$DEFAULT_DIR")"; then
    status=$?
    if [[ "$status" -eq 1 ]]; then
      print "Installer canceled."
      exit 1
    fi
    print "TUI failed (status $status). Falling back to text prompts."
    USE_TUI=0
    TARGET_DIR="$DEFAULT_DIR"
  fi
else
  TARGET_DIR="$DEFAULT_DIR"
fi
log "Selected dir: $TARGET_DIR"

if [[ "$TARGET_DIR" == "~"* ]]; then
  TARGET_DIR="${TARGET_DIR/#\~/$HOME}"
fi

REPO_STATE="missing"
if [[ -d "$TARGET_DIR/.git" ]]; then
  REPO_STATE="git"
elif [[ -e "$TARGET_DIR" ]]; then
  REPO_STATE="dir"
fi
log "Repo state: $REPO_STATE"

REPO_ACTION="clone"
if [[ -n "${CODEX_SPEECH_REPO_ACTION:-}" ]]; then
  REPO_ACTION="$CODEX_SPEECH_REPO_ACTION"
else
  if [[ "$USE_TUI" == "1" ]]; then
    if [[ "$REPO_STATE" == "git" ]]; then
      if ! REPO_ACTION="$(tui_menu "Repository found at $TARGET_DIR" \
        update "Update existing repo (git pull)" \
        use "Use as-is (no update)" \
        reclone "Delete and re-clone")"; then
        status=$?
        if [[ "$status" -eq 1 ]]; then
          print "Installer canceled."
          exit 1
        fi
        print "TUI failed (status $status). Falling back to text prompts."
        USE_TUI=0
      fi
    elif [[ "$REPO_STATE" == "dir" ]]; then
      if ! REPO_ACTION="$(tui_menu "Directory exists at $TARGET_DIR" \
        use "Use directory as-is" \
        reclone "Delete and re-clone" \
        abort "Abort")"; then
        status=$?
        if [[ "$status" -eq 1 ]]; then
          print "Installer canceled."
          exit 1
        fi
        print "TUI failed (status $status). Falling back to text prompts."
        USE_TUI=0
      fi
      if [[ "$REPO_ACTION" == "abort" ]]; then
        print "Aborted."
        exit 1
      fi
    else
      REPO_ACTION="clone"
    fi
  else
    :
  fi
fi

if [[ -z "$REPO_ACTION" ]]; then
  if [[ "$REPO_STATE" == "git" ]]; then
    if confirm "Repo exists at $TARGET_DIR. Update it now?" "y"; then
      REPO_ACTION="update"
    else
      REPO_ACTION="use"
    fi
  elif [[ "$REPO_STATE" == "dir" ]]; then
    if confirm "Directory exists at $TARGET_DIR but is not a git repo. Use it anyway?" "n"; then
      REPO_ACTION="use"
    else
      print "Choose an empty directory or an existing codex-speech clone."
      exit 1
    fi
  else
    REPO_ACTION="clone"
  fi
fi
log "Repo action: $REPO_ACTION"

INSTALL_BACKEND=0
INSTALL_ANDROID=0
INSTALL_GUI=0
TUI_COMPONENTS_FAILED=0

if [[ -n "${CODEX_SPEECH_COMPONENTS:-}" ]]; then
  comps="${CODEX_SPEECH_COMPONENTS}"
  [[ "$comps" == *"backend"* ]] && INSTALL_BACKEND=1
  [[ "$comps" == *"android"* ]] && INSTALL_ANDROID=1
  [[ "$comps" == *"gui"* ]] && INSTALL_GUI=1
else
  if [[ "$USE_TUI" == "1" ]]; then
    if ! choices="$(tui_checklist "Select components to install" \
      backend "Backend (FastAPI + PM2)" ON \
      android "Android viewer app" ON \
      gui "Linux GUI (GTK4 + VTE)" "$([ "$DEFAULT_GUI" == "1" ] && echo ON || echo OFF)")"; then
      status=$?
      if [[ "$status" -eq 1 ]]; then
        print "Installer canceled."
        exit 1
      fi
      print "TUI failed (status $status). Falling back to defaults."
      USE_TUI=0
      TUI_COMPONENTS_FAILED=1
      choices=""
    fi
    [[ "$choices" == *"backend"* ]] && INSTALL_BACKEND=1
    [[ "$choices" == *"android"* ]] && INSTALL_ANDROID=1
    [[ "$choices" == *"gui"* ]] && INSTALL_GUI=1
  else
    :
  fi
fi

if [[ "$INSTALL_BACKEND" == "0" && "$INSTALL_ANDROID" == "0" && "$INSTALL_GUI" == "0" ]]; then
  if [[ "$TUI_COMPONENTS_FAILED" == "1" ]]; then
    INSTALL_BACKEND=1
    INSTALL_ANDROID=1
    INSTALL_GUI="$DEFAULT_GUI"
  fi
fi
log "Components: backend=$INSTALL_BACKEND android=$INSTALL_ANDROID gui=$INSTALL_GUI"

if [[ "$INSTALL_BACKEND" == "0" && "$INSTALL_ANDROID" == "0" && "$INSTALL_GUI" == "0" ]]; then
  print "Nothing selected to install. Exiting."
  exit 1
fi

INSTALL_APK="${CODEX_SPEECH_INSTALL_APK:-1}"
START_BACKEND="${CODEX_SPEECH_START_BACKEND:-1}"
INSTALL_APK_VIEWER=1

if [[ -n "${CODEX_SPEECH_APK_TARGETS:-}" ]]; then
  targets="${CODEX_SPEECH_APK_TARGETS}"
  INSTALL_APK_VIEWER=0
  [[ "$targets" == *"viewer"* ]] && INSTALL_APK_VIEWER=1
fi

if [[ "$USE_TUI" == "1" ]]; then
  if [[ "$INSTALL_ANDROID" == "1" ]]; then
    if tui_yesno "Install APK to connected Android device?"; then
      INSTALL_APK=1
    else
      INSTALL_APK=0
    fi
    if [[ "$INSTALL_APK" == "1" ]]; then
      if choices="$(tui_checklist "Select APKs to install" \
        viewer "Android Viewer" ON)"; then
        INSTALL_APK_VIEWER=0
        [[ "$choices" == *"viewer"* ]] && INSTALL_APK_VIEWER=1
      fi
    fi
  fi
  if [[ "$INSTALL_BACKEND" == "1" ]]; then
    if tui_yesno "Start backend with PM2 after install?"; then
      START_BACKEND=1
    else
      START_BACKEND=0
    fi
    if [[ "$USE_TUI" == "1" ]]; then
      if ! BACKEND_PORT="$(tui_input "Backend port" "$BACKEND_PORT_DEFAULT")"; then
        status=$?
        if [[ "$status" -eq 1 ]]; then
          print "Installer canceled."
          exit 1
        fi
        print "TUI failed (status $status). Falling back to default port."
        BACKEND_PORT="$BACKEND_PORT_DEFAULT"
      fi
    fi
  fi
  summary="Target: $TARGET_DIR\nRepo action: $REPO_ACTION\nComponents:"
  [[ "$INSTALL_BACKEND" == "1" ]] && summary+=" backend"
  [[ "$INSTALL_ANDROID" == "1" ]] && summary+=" android"
  [[ "$INSTALL_GUI" == "1" ]] && summary+=" gui"
  [[ "$INSTALL_BACKEND" == "1" ]] && summary+="\nBackend port: $BACKEND_PORT"
  summary+="\n\nContinue?"
  if ! tui_yesno "$summary"; then
    print "Aborted."
    exit 1
  fi
else
  print "\n=== Summary ==="
  print "Target: $TARGET_DIR"
  print "Repo action: $REPO_ACTION"
  print "Components: backend=$INSTALL_BACKEND android=$INSTALL_ANDROID gui=$INSTALL_GUI"
  if [[ "$INSTALL_BACKEND" == "1" ]]; then
    print "Backend port: $BACKEND_PORT"
  fi
fi

if [[ "$REPO_ACTION" == "reclone" ]]; then
  if [[ -e "$TARGET_DIR" ]]; then
    if [[ "$USE_TUI" == "1" ]]; then
      if ! tui_yesno "This will delete $TARGET_DIR. Continue?"; then
        print "Aborted."
        exit 1
      fi
    else
      if ! confirm "This will delete $TARGET_DIR. Continue?" "n"; then
        print "Aborted."
        exit 1
      fi
    fi
    rm -rf "$TARGET_DIR"
  fi
  REPO_ACTION="clone"
fi

if [[ "$REPO_ACTION" == "clone" ]]; then
  print "Cloning $REPO_URL -> $TARGET_DIR"
  git clone "$REPO_URL" "$TARGET_DIR"
  REPO_DIR="$TARGET_DIR"
elif [[ "$REPO_ACTION" == "update" ]]; then
  print "Updating repo in $TARGET_DIR"
  git -C "$TARGET_DIR" pull --ff-only
  REPO_DIR="$TARGET_DIR"
else
  REPO_DIR="$TARGET_DIR"
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

COMMON_PACKAGES=(git curl unzip zip)
PY_PACKAGES=(python3 python3-venv python3-pip)
BACKEND_PACKAGES=(build-essential ffmpeg nodejs)
ANDROID_PACKAGES=(openjdk-17-jdk android-sdk-platform-tools)

PACKAGES=("${COMMON_PACKAGES[@]}")
if [[ "$INSTALL_BACKEND" == "1" || "$INSTALL_GUI" == "1" ]]; then
  PACKAGES+=("${PY_PACKAGES[@]}")
fi
if [[ "$INSTALL_BACKEND" == "1" ]]; then
  PACKAGES+=("${BACKEND_PACKAGES[@]}")
fi
if [[ "$INSTALL_ANDROID" == "1" ]]; then
  PACKAGES+=("${ANDROID_PACKAGES[@]}")
fi

if [[ ${#PACKAGES[@]} -gt 0 ]]; then
  apt_install "${PACKAGES[@]}"
fi

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

if [[ "$INSTALL_BACKEND" == "1" ]]; then
  ensure_npm_or_pnpm
  if ! command -v pm2 >/dev/null 2>&1; then
    print "\n=== Installing PM2 ==="
    if command -v npm >/dev/null 2>&1; then
      $SUDO npm install -g pm2
    else
      $SUDO pnpm add -g pm2
    fi
  fi
fi

if [[ "$INSTALL_GUI" == "1" ]]; then
  print "\n=== Installing Linux GUI dependencies ==="
  apt_install \
    python3-dev \
    libgtk-4-dev libvte-2.91-gtk4-0 libvte-2.91-gtk4-dev \
    gir1.2-vte-3.91 libgirepository1.0-dev libgirepository-2.0-dev \
    portaudio19-dev libasound2-dev pulseaudio \
    gstreamer1.0-tools gstreamer1.0-plugins-base gstreamer1.0-plugins-good \
    pkg-config xvfb
fi

if [[ "$INSTALL_ANDROID" == "1" ]]; then
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

  if ! command -v sdkmanager >/dev/null 2>&1; then
    print "sdkmanager not found after installing command line tools."
    exit 1
  fi

  set +e
  yes | sdkmanager --licenses >/dev/null
  LICENSE_STATUS=$?
  set -e
  if [[ "$LICENSE_STATUS" -ne 0 && "$LICENSE_STATUS" -ne 141 ]]; then
    print "sdkmanager --licenses failed with status $LICENSE_STATUS"
    exit "$LICENSE_STATUS"
  fi

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
fi

if [[ "$INSTALL_BACKEND" == "1" ]]; then
  print "\n=== Setting up backend ==="
  python3 -m venv "$REPO_DIR/apps/backend/.venv"
  "$REPO_DIR/apps/backend/.venv/bin/pip" install --upgrade pip
  "$REPO_DIR/apps/backend/.venv/bin/pip" install -r "$REPO_DIR/apps/backend/requirements.txt"

  if [[ "$START_BACKEND" == "1" ]]; then
    print "\n=== Starting backend via PM2 ==="
    ( cd "$REPO_DIR" && CODEX_BACKEND_PORT="$BACKEND_PORT" pm2 start ecosystem.config.js --only codex-backend --update-env ) \
      || CODEX_BACKEND_PORT="$BACKEND_PORT" pm2 restart codex-backend --update-env
  fi
fi

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

if [[ "$INSTALL_ANDROID" == "1" ]]; then
  print "\n=== Building Android APK ==="
  chmod +x "$REPO_DIR/apps/android-viewer/gradle-8.5/bin/gradle"
  "$REPO_DIR/apps/android-viewer/gradle-8.5/bin/gradle" -p "$REPO_DIR/apps/android-viewer" :app:assembleDebug
  APK_VIEWER="$REPO_DIR/apps/android-viewer/app/build/outputs/apk/debug/app-debug.apk"

  if [[ "$INSTALL_APK" == "1" ]]; then
    print "\n=== Installing APK to device (optional) ==="
    if command -v adb >/dev/null 2>&1; then
      mapfile -t DEVICES < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
      TARGET_DEVICE=""
      if [[ ${#DEVICES[@]} -eq 1 ]]; then
        TARGET_DEVICE="${DEVICES[0]}"
      elif [[ ${#DEVICES[@]} -gt 1 ]]; then
        print "Select device to install APK:"
        select SERIAL in "${DEVICES[@]}" "Skip"; do
          if [[ "$SERIAL" == "Skip" ]]; then
            break
          fi
          if [[ -n "$SERIAL" ]]; then
            TARGET_DEVICE="$SERIAL"
            break
          fi
        done
      else
        print "No Android devices detected. APKs built at:"
        print "Viewer: $APK_VIEWER"
      fi
      if [[ -n "$TARGET_DEVICE" ]]; then
        if [[ "$INSTALL_APK_VIEWER" == "0" ]]; then
          print "No APKs selected for install."
        fi
        if [[ "$INSTALL_APK_VIEWER" == "1" ]]; then
          print "Installing viewer to $TARGET_DEVICE"
          adb -s "$TARGET_DEVICE" install -r "$APK_VIEWER"
        fi
      fi
    else
      print "adb not found. APKs built at:"
      print "Viewer: $APK_VIEWER"
    fi
  fi
fi

if command -v npx >/dev/null 2>&1; then
  print "\n=== Registering Android MCP ==="
  ensure_android_mcp
else
  print "\n=== Skipping Android MCP (npx not found) ==="
fi

print "\n=== Done ==="
if [[ "$INSTALL_BACKEND" == "1" ]]; then
  print "Backend: http://<this-machine-ip>:$BACKEND_PORT"
fi
if [[ "$INSTALL_ANDROID" == "1" ]]; then
  print "Android APK (viewer): $APK_VIEWER"
  print "Open Codex Speech on your phone and connect to your LAN IP."
fi
