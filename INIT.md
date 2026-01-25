```markdown
# Linux GUI Tool für Codex CLI mit STT-Integration - Detaillierte technische Spezifikation

Erstelle ein modernes, natives Linux GUI-Tool für Ubuntu mit vollständiger Speech-to-Text Integration und Codex CLI Terminal-Emulation.

## 1. Architektur & Technologie-Stack

### 1.1 Framework-Wahl
**Primäre Empfehlung: Python + GTK4 + PyGObject**
- GTK4 für native Linux-Integration
- PyGObject 3.42+ für Python-GTK Bindings
- VTE (libvte-2.91) für Terminal-Emulation
- GStreamer für Audio-Pipeline
- Vosk für offline Speech-to-Text

**Alternative: Qt6 + C++/Python**
- Qt6.5+ mit QML für modernes UI
- QTermWidget für Terminal-Emulation
- Qt Multimedia für Audio
- Vosk-API C++ Bindings

**Begründung der Wahl basierend auf:**
- Native Linux Desktop Integration
- Performance bei Terminal-Streaming
- STT-Library Kompatibilität
- Entwicklungs- und Wartungsaufwand

### 1.2 Prozess-Architektur
```
Main GUI Process
├── STT Thread/Process (Audio-Capture + Recognition)
├── Terminal PTY Process (Codex CLI subprocess)
├── IPC Message Queue (STT → Terminal)
└── UI Event Loop (GTK/Qt Main Thread)
```

**Threading-Model:**
- Haupt-UI-Thread: GTK/Qt Event Loop
- STT-Worker-Thread: Audio-Aufnahme + Vosk-Verarbeitung (non-blocking)
- Terminal-IO-Thread: PTY reads/writes zum Codex subprocess
- IPC via Thread-safe Queues (Python: queue.Queue, C++: QQueue mit Mutex)

## 2. UI/UX Design - Detaillierte Spezifikation

### 2.1 Hauptfenster Layout
```
┌─────────────────────────────────────────────────────────────────┐
│ ☰ Codex STT Assistant    [🌓 Theme] [⚙️ Settings]      [─][□][×]│
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────────┬──────────────────────────────────┐  │
│  │  STT INPUT PANEL     │  CODEX TERMINAL VIEW             │  │
│  │  (30% width)         │  (70% width)                     │  │
│  │                      │                                  │  │
│  │  [🎤] Record         │  $ codex                         │  │
│  │  [⏹️] Stop           │  > Starting interactive mode...  │  │
│  │                      │                                  │  │
│  │  ┌─────────────────┐│  [Terminal Output Area]          │  │
│  │  │ Text Input      ││  - Full ANSI colors              │  │
│  │  │ (Multi-line)    ││  - Scrollable                    │  │
│  │  │                 ││  - Copyable                      │  │
│  │  │                 ││  - Search function               │  │
│  │  └─────────────────┘│                                  │  │
│  │                      │                                  │  │
│  │  [✓] Auto-Submit    │                                  │  │
│  │  [Send] [Clear]     │                                  │  │
│  │                      │                                  │  │
│  │  ── History ──      │                                  │  │
│  │  • Previous input 1 │                                  │  │
│  │  • Previous input 2 │                                  │  │
│  │  • Previous input 3 │                                  │  │
│  │                      │                                  │  │
│  └──────────────────────┴──────────────────────────────────┘  │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│ 🟢 Codex: Running | STT: Vosk-DE loaded | Mic: Built-in | 14:32│
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Komponenten-Details

#### 2.2.1 STT Input Panel (Linke Seite)
**Mikrofonsteuerung:**
- Großer, zentraler Mikrofon-Button (64x64px, animiert)
- States: Idle (grau), Recording (rot pulsierend), Processing (gelb rotierend)
- Visuelles Audio-Level-Meter (Wellenform oder Balken-Anzeige)
- Echtzeit-Transkription während der Aufnahme (partial results)

**Texteingabe-Widget:**
- GtkTextView (GTK) oder QPlainTextEdit (Qt)
- Minimale Höhe: 100px, maximale Höhe: 300px, scrollbar bei Bedarf
- Syntax-Highlighting für Code-Snippets (optional, erkennbar über Backticks)
- Placeholder-Text: "Sprechen Sie oder tippen Sie hier... (Strg+Space zum Aufnehmen)"
- Zeichenzähler unten rechts im Eingabefeld

**Control Buttons:**
- "Send" Button: Primärer Action-Button, grün, mit Keyboard-Shortcut-Label
- "Clear" Button: Sekundärer Button, löscht aktuellen Input
- Auto-Submit Toggle: Checkbox mit Tooltip "Eingabe automatisch nach STT senden"

**History-Liste:**
- Scrollbare Liste der letzten 50 Eingaben
- Timestamps für jede Eingabe
- Click-to-reuse: Klick auf History-Item kopiert Text ins Eingabefeld
- Context-Menu: Kopieren, Löschen, Alle löschen
- Persistierung in SQLite-DB oder JSON-File

#### 2.2.2 Codex Terminal View (Rechte Seite)
**Terminal-Emulator Integration:**
- VTE Widget (libvte-2.91) für GTK ODER QTermWidget für Qt
- Vollständige VT100/xterm-256color Unterstützung
- ANSI-Escape-Sequenzen für Farben, Formatierung, Cursor-Control
- Scrollback-Buffer: 10.000 Zeilen
- Font: Monospace, konfigurierbare Größe (Standard: 11pt)
- Copy-on-select Verhalten (wie gnome-terminal)

**Terminal-Prozess-Management:**
- Spawne `codex` CLI via PTY (Pseudoterminal)
- Kommando: `codex` (ohne exec mode flags)
- Working Directory: Konfigurierbar, Standard: $HOME oder letztes verwendetes
- Environment Variables: Vererbe vom Parent, setze TERM=xterm-256color
- Automatischer Restart bei Prozess-Crash mit User-Benachrichtigung

**Terminal-Interaktionen:**
- Input-Injection: Texte vom STT-Panel werden als stdin eingespeist
- Simulation von Keyboard-Enter nach Text-Injection
- Strg+C Interrupt-Signal Unterstützung
- Context-Menu: Copy, Paste, Clear Screen, Save Output

**Output-Capture Features:**
- "Save Session" Button: Exportiert Terminal-Output als .txt oder .log
- Search-in-Terminal Funktion: Highlight-Suche wie in modernen Terminals
- Auto-Scroll Toggle: Automatisch zum Ende scrollen oder Position halten

### 2.3 Styling & Theming

#### Dark Mode (Standard)
```css
Background: #1e1e2e (Hauptfenster)
STT Panel: #2b2b3c (Hintergrund), #ffffff (Text)
Terminal: #0a0a0f (Hintergrund), #e0e0e0 (Text)
Accent Color: #89b4fa (Buttons, Links)
Recording Indicator: #f38ba8 (Rot pulsierend)
Borders: #45475a (Subtile Trenner)
```

#### Light Mode
```css
Background: #eff1f5 (Hauptfenster)
STT Panel: #ffffff (Hintergrund), #1e1e2e (Text)
Terminal: #f5f5f5 (Hintergrund), #2b2b3c (Text)
Accent Color: #1e66f5 (Buttons, Links)
Recording Indicator: #d20f39 (Rot pulsierend)
Borders: #dce0e8 (Subtile Trenner)
```

**CSS/QSS Implementation:**
- GTK: Lade custom CSS via GtkCssProvider
- Qt: Verwende QSS Stylesheets
- Theme-Wechsel ohne Neustart via Runtime-CSS-Update

### 2.4 Fenster-Management
- Minimale Fenstergröße: 1000x600px
- Standard-Größe: 1400x800px
- Maximierbar, Vollbild-fähig (F11)
- Splitter zwischen Panels: Resizable, Position wird gespeichert (20-80% Range)
- Window-State-Persistenz: Größe, Position, Splitter-Ratio in Config-File

## 3. Speech-to-Text Integration - Detailliert

### 3.1 Vosk-Integration (Primär)
**Modell-Download & Management:**
- Auto-Download bei erstem Start über Vosk-API
- Deutsche Modelle:
  * `vosk-model-de-0.21` (Klein, ~45MB, schnell)
  * `vosk-model-de-tuda-0.6` (Groß, ~1.9GB, genauer - optional)
- Englische Modelle:
  * `vosk-model-small-en-us-0.15` (~40MB)
- Speicherort: `~/.local/share/codex-stt-assistant/models/`
- Model-Selector im Settings-Dialog

**Audio-Pipeline:**
```python
# Pseudocode für Audio-Capture
import pyaudio
import vosk
import json

# Setup
RATE = 16000  # Vosk benötigt 16kHz
CHUNK = 4096

p = pyaudio.PyAudio()
stream = p.open(
    format=pyaudio.paInt16,
    channels=1,
    rate=RATE,
    input=True,
    frames_per_buffer=CHUNK,
    input_device_index=selected_device
)

model = vosk.Model(model_path)
recognizer = vosk.KaldiRecognizer(model, RATE)

# Recording Loop (in separatem Thread)
while recording:
    data = stream.read(CHUNK, exception_on_overflow=False)
    if recognizer.AcceptWaveform(data):
        result = json.loads(recognizer.Result())
        final_text = result.get('text', '')
        # Sende final_text zu UI Thread via Queue
    else:
        partial = json.loads(recognizer.PartialResult())
        partial_text = partial.get('partial', '')
        # Update UI mit partial_text (Echtzeit-Feedback)
```

**Threading-Implementierung:**
- Separater Thread für Audio-Capture (blocking I/O)
- Separater Thread für Vosk-Processing (CPU-intensiv)
- Queue-basierte Kommunikation: Audio → Vosk → GUI
- GLib.idle_add() (GTK) oder QMetaObject.invokeMethod() (Qt) für UI-Updates

**Error-Handling:**
- Audio-Device nicht verfügbar: Zeige Fehlerdialog, liste verfügbare Devices
- Modell-Ladefehler: Fallback auf Download-Dialog
- Out-of-Memory bei großen Modellen: Warnung + Vorschlag kleineres Modell

### 3.2 Alternative STT-Engines (Konfigurierbar)

#### Whisper.cpp Integration
```cpp
// C++ Binding für whisper.cpp
#include "whisper.h"

struct whisper_context* ctx = whisper_init_from_file(model_path);
whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
params.language = "de";
params.translate = false;

// Audio-Buffer zu Whisper
whisper_full(ctx, params, audio_samples, n_samples);

// Ergebnis extrahieren
int n_segments = whisper_full_n_segments(ctx);
for (int i = 0; i < n_segments; i++) {
    const char* text = whisper_full_get_segment_text(ctx, i);
    // Text an GUI senden
}
```

#### Mozilla DeepSpeech (Deprecated, aber als Option)
- Hinweis im UI: "DeepSpeech ist nicht mehr maintained, Vosk empfohlen"
- Falls gewählt: Download von DeepSpeech 0.9.3 Modellen

**Engine-Auswahl im Settings:**
- Radio-Buttons: Vosk, Whisper.cpp, DeepSpeech, Custom (API-Endpoint)
- Custom API: Webhook-URL für Google Cloud STT, Azure, etc.

### 3.3 Audio-Device-Management
**Geräteerkennung:**
```python
import pyaudio

p = pyaudio.PyAudio()
devices = []
for i in range(p.get_device_count()):
    info = p.get_device_info_by_index(i)
    if info['maxInputChannels'] > 0:
        devices.append({
            'index': i,
            'name': info['name'],
            'channels': info['maxInputChannels'],
            'rate': int(info['defaultSampleRate'])
        })
```

**UI für Device-Auswahl:**
- Dropdown im Settings mit allen verfügbaren Input-Devices
- Test-Button: 3 Sekunden Aufnahme + Playback zur Überprüfung
- Level-Meter: Echtzeit-Audio-Pegel auch ohne Aufnahme (zur Kalibrierung)

### 3.4 VAD (Voice Activity Detection)
**Optional: Automatische Start/Stop-Erkennung**
- Webrtcvad Library Integration
- Aggressiveness-Level: 0-3 (konfigurierbar)
- Auto-Start: Beginne Aufnahme bei Spracherkennung
- Auto-Stop: Beende nach X Sekunden Stille (Standard: 1.5s)
- Toggle im UI: "Auto VAD" Checkbox

## 4. Terminal-Integration - Detailliert

### 4.1 PTY (Pseudoterminal) Setup
**VTE Widget Konfiguration (GTK):**
```python
import gi
gi.require_version('Vte', '2.91')
from gi.repository import Vte, GLib

terminal = Vte.Terminal()
terminal.spawn_async(
    Vte.PtyFlags.DEFAULT,
    os.getcwd(),  # Working directory
    ['/usr/bin/codex'],  # Command
    None,  # Environment (None = inherit)
    GLib.SpawnFlags.DEFAULT,
    None, None,  # Child setup
    -1,  # Timeout
    None,  # Cancellable
    callback_function,  # Callback bei Prozess-Ende
    None  # User data
)

# Terminal-Konfiguration
terminal.set_font(Pango.FontDescription('Monospace 11'))
terminal.set_scrollback_lines(10000)
terminal.set_scroll_on_output(True)
terminal.set_cursor_blink_mode(Vte.CursorBlinkMode.ON)
terminal.set_color_scheme(dark_mode_colors)
```

**QTermWidget Setup (Qt):**
```cpp
#include <qtermwidget5/qtermwidget.h>

QTermWidget* terminal = new QTermWidget(0, this);
terminal->setShellProgram("/usr/bin/codex");
terminal->setColorScheme("Linux");
terminal->setScrollBarPosition(QTermWidget::ScrollBarRight);
terminal->setTerminalFont(QFont("Monospace", 11));
terminal->startShellProgram();

// Signal-Handling
connect(terminal, &QTermWidget::finished, this, &MainWindow::onTerminalExit);
```

### 4.2 Input-Injection Mechanismus
**Text an Terminal senden:**
```python
def send_to_terminal(text):
    # Entferne Newlines, füge eigenes am Ende hinzu
    cleaned_text = text.strip().replace('\n', ' ')

    # Sende Text zum PTY
    terminal.feed_child(cleaned_text.encode('utf-8'))

    # Simuliere Enter-Taste
    terminal.feed_child(b'\n')

    # Optional: Kurze Verzögerung für visuelle Bestätigung
    GLib.timeout_add(100, lambda: terminal.set_scroll_on_output(True))
```

**Multi-Line-Input Handling:**
- Bei mehreren Zeilen: Zeile für Zeile senden mit delay (100ms)
- Oder: Gesamten Text senden + Newline am Ende
- Konfigurierbar via Checkbox: "Send line-by-line" vs "Send as block"

### 4.3 Terminal-Prozess-Lifecycle
**Prozess-Monitoring:**
```python
def on_terminal_process_exit(terminal, status):
    if status != 0:
        # Fehlerfall
        show_error_dialog(f"Codex exited with status {status}")
        show_restart_button()
    else:
        # Normales Exit
        show_info_banner("Codex session ended")

def restart_terminal():
    terminal.reset(True, True)  # Clear screen + scrollback
    terminal.spawn_async(...)  # Neustart wie oben
```

**Auto-Restart-Option:**
- Checkbox im Settings: "Auto-restart Codex on crash"
- Countdown-Dialog: "Codex crashed. Restarting in 5... 4... 3..." mit Cancel-Button

### 4.4 Terminal-Features

**Copy/Paste:**
```python
# Copy (Strg+Shift+C)
def copy_selection():
    if terminal.get_has_selection():
        terminal.copy_clipboard_format(Vte.Format.TEXT)

# Paste (Strg+Shift+V)
def paste_clipboard():
    clipboard = Gtk.Clipboard.get(Gdk.SELECTION_CLIPBOARD)
    text = clipboard.wait_for_text()
    if text:
        terminal.paste_clipboard()
```

**Search in Terminal:**
```python
import re

search_term = "error"
buffer = terminal.get_text_range(0, 0, -1, -1, lambda *args: True)[0]
matches = [m.start() for m in re.finditer(search_term, buffer, re.IGNORECASE)]

# Highlight matches
for pos in matches:
    row, col = calculate_row_col(pos, buffer)
    terminal.select_text(row, col, row, col + len(search_term))
```

**Session-Export:**
```python
def export_session():
    # Kompletten Terminal-Buffer extrahieren
    full_text = terminal.get_text_range(0, 0, -1, -1, lambda *args: True)[0]

    # Timestamp im Dateinamen
    filename = f"codex_session_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"
    filepath = os.path.join(os.path.expanduser('~'), filename)

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(full_text)

    show_notification(f"Session saved to {filepath}")
```

## 5. Konfiguration & Settings

### 5.1 Settings-Dialog Design
**Tabbed Interface:**
```
┌─────────────────────────────────────────┐
│ ⚙️ Settings                        [×]  │
├─────────────────────────────────────────┤
│ [General] [STT] [Terminal] [Audio]     │
├─────────────────────────────────────────┤
│                                         │
│  General Tab:                           │
│  • Theme: ⚪ Light ⚫ Dark 🔵 Auto     │
│  • Auto-Submit: ☑️ Enabled             │
│  • History Size: [50] entries           │
│  • Save window state: ☑️               │
│                                         │
│  STT Tab:                               │
│  • Engine: [Vosk ▼]                     │
│  • Language: [Deutsch ▼]                │
│  • Model: [vosk-model-de-0.21 ▼]        │
│  • [Download Models...]                 │
│  • VAD: ☑️ Auto Voice Detection        │
│  • VAD Aggressiveness: [██░░] 2/3       │
│                                         │
│  Terminal Tab:                          │
│  • Font: [Monospace ▼] Size: [11▼]     │
│  • Scrollback: [10000] lines            │
│  • Working Dir: [~/] [Browse...]        │
│  • Codex Path: [/usr/bin/codex] [...]   │
│  • Auto-restart: ☑️                     │
│                                         │
│  Audio Tab:                             │
│  • Input Device: [Built-in Mic ▼]       │
│  • Sample Rate: [16000 Hz]              │
│  • [Test Recording]                     │
│  • Input Level: [████████░░] -12dB      │
│                                         │
├─────────────────────────────────────────┤
│              [Cancel] [Apply] [OK]      │
└─────────────────────────────────────────┘
```

### 5.2 Konfigurations-File Format
**~/.config/codex-stt-assistant/config.json:**
```json
{
  "version": "1.0.0",
  "ui": {
    "theme": "dark",
    "window": {
      "width": 1400,
      "height": 800,
      "x": 100,
      "y": 100,
      "maximized": false,
      "splitter_position": 0.3
    },
    "auto_submit": true,
    "history_size": 50
  },
  "stt": {
    "engine": "vosk",
    "language": "de",
    "model_path": "~/.local/share/codex-stt-assistant/models/vosk-model-de-0.21",
    "vad_enabled": true,
    "vad_aggressiveness": 2,
    "auto_stop_silence_duration": 1.5
  },
  "terminal": {
    "font_family": "Monospace",
    "font_size": 11,
    "scrollback_lines": 10000,
    "working_directory": "~",
    "codex_path": "/usr/bin/codex",
    "codex_args": [],
    "auto_restart_on_crash": true,
    "send_mode": "block"
  },
  "audio": {
    "device_index": 0,
    "device_name": "Built-in Microphone",
    "sample_rate": 16000,
    "chunk_size": 4096
  },
  "advanced": {
    "log_level": "INFO",
    "log_file": "~/.local/share/codex-stt-assistant/app.log",
    "check_updates": true
  }
}
```

**Config-Management:**
```python
import json
import os
from pathlib import Path

class Config:
    CONFIG_DIR = Path.home() / '.config' / 'codex-stt-assistant'
    CONFIG_FILE = CONFIG_DIR / 'config.json'
    DEFAULT_CONFIG = {...}  # Wie oben

    @classmethod
    def load(cls):
        if not cls.CONFIG_FILE.exists():
            cls.CONFIG_DIR.mkdir(parents=True, exist_ok=True)
            cls.save(cls.DEFAULT_CONFIG)
            return cls.DEFAULT_CONFIG

        with open(cls.CONFIG_FILE, 'r') as f:
            return json.load(f)

    @classmethod
    def save(cls, config):
        with open(cls.CONFIG_FILE, 'w') as f:
            json.dump(config, f, indent=2)

    @classmethod
    def get(cls, key_path, default=None):
        # key_path z.B. "ui.window.width"
        config = cls.load()
        keys = key_path.split('.')
        value = config
        for key in keys:
            value = value.get(key, {})
        return value if value != {} else default

    @classmethod
    def set(cls, key_path, value):
        config = cls.load()
        keys = key_path.split('.')
        target = config
        for key in keys[:-1]:
            target = target.setdefault(key, {})
        target[keys[-1]] = value
        cls.save(config)
```

### 5.3 Model-Download-Manager
**UI für Modell-Downloads:**
```python
class ModelDownloader(Gtk.Dialog):
    MODELS = {
        'vosk-de-small': {
            'name': 'Deutsch (Klein, schnell)',
            'url': 'https://alphacephei.com/vosk/models/vosk-model-de-0.21.zip',
            'size': '45 MB'
        },
        'vosk-de-large': {
            'name': 'Deutsch (Groß, genau)',
            'url': 'https://alphacephei.com/vosk/models/vosk-model-de-tuda-0.6.zip',
            'size': '1.9 GB'
        },
        'vosk-en-small': {
            'name': 'English (Small)',
            'url': 'https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip',
            'size': '40 MB'
        }
    }

    def download_model(self, model_key):
        # Download mit Progress-Bar
        import requests
        from zipfile import ZipFile

        url = self.MODELS[model_key]['url']
        target_dir = Path.home() / '.local/share/codex-stt-assistant/models'
        target_dir.mkdir(parents=True, exist_ok=True)

        # Download
        response = requests.get(url, stream=True)
        total_size = int(response.headers.get('content-length', 0))

        zip_path = target_dir / f'{model_key}.zip'
        with open(zip_path, 'wb') as f:
            downloaded = 0
            for chunk in response.iter_content(chunk_size=8192):
                f.write(chunk)
                downloaded += len(chunk)
                progress = downloaded / total_size
                self.update_progress_bar(progress)

        # Extract
        self.set_status("Extracting...")
        with ZipFile(zip_path, 'r') as zip_ref:
            zip_ref.extractall(target_dir)

        zip_path.unlink()  # Lösche ZIP
        self.set_status("Download complete!")
```

## 6. Keyboard Shortcuts - Vollständige Liste

```python
SHORTCUTS = {
    # STT-Steuerung
    '<Primary>space': 'toggle_recording',  # Strg+Space
    '<Primary><Shift>space': 'cancel_recording',  # Strg+Shift+Space

    # Input-Steuerung
    '<Primary>Return': 'send_input',  # Strg+Enter
    '<Primary><Shift>Return': 'send_input_new_line',  # Strg+Shift+Enter (Multi-Line)
    '<Primary>l': 'clear_input',  # Strg+L

    # Terminal-Steuerung
    '<Primary><Shift>c': 'terminal_copy',  # Strg+Shift+C
    '<Primary><Shift>v': 'terminal_paste',  # Strg+Shift+V
    '<Primary><Shift>k': 'terminal_clear',  # Strg+Shift+K
    '<Primary><Shift>f': 'terminal_search',  # Strg+Shift+F

    # Fenster-Management
    'F11': 'toggle_fullscreen',
    '<Primary>q': 'quit_application',
    '<Primary>comma': 'open_settings',  # Strg+,

    # Theme & UI
    '<Primary><Shift>t': 'toggle_theme',  # Strg+Shift+T
    '<Primary>1': 'focus_input_panel',  # Strg+1
    '<Primary>2': 'focus_terminal_panel',  # Strg+2

    # History
    '<Primary>h': 'show_history',  # Strg+H
    '<Alt>Up': 'history_previous',  # Alt+Up
    '<Alt>Down': 'history_next',  # Alt+Down

    # Spezial
    '<Primary><Shift>s': 'save_terminal_session',  # Strg+Shift+S
    'F1': 'show_help',  # Hilfe-Dialog
    '<Primary><Shift>r': 'restart_codex'  # Strg+Shift+R
}

# GTK Accelerator Setup
accel_group = Gtk.AccelGroup()
window.add_accel_group(accel_group)

for shortcut, action in SHORTCUTS.items():
    key, mods = Gtk.accelerator_parse(shortcut)
    accel_group.connect(key, mods, Gtk.AccelFlags.VISIBLE,
                       lambda *args, a=action: handle_shortcut(a))
```

**Shortcuts-Cheatsheet-Dialog:**
- F1 öffnet Overlay mit allen Shortcuts
- Kategorisiert: STT, Terminal, Window, etc.
- Suchfunktion im Cheatsheet

## 7. Fehlerbehandlung & Logging

### 7.1 Logging-System
```python
import logging
from pathlib import Path

LOG_DIR = Path.home() / '.local/share/codex-stt-assistant'
LOG_FILE = LOG_DIR / 'app.log'

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(LOG_FILE),
        logging.StreamHandler()  # Auch in Console
    ]
)

logger = logging.getLogger('CodexSTT')

# Usage
logger.info("Application started")
logger.warning("Audio device not found, using default")
logger.error("Failed to load Vosk model", exc_info=True)
```

### 7.2 Error-Dialogs
```python
def show_error(title, message, details=None):
    dialog = Gtk.MessageDialog(
        transient_for=main_window,
        flags=0,
        message_type=Gtk.MessageType.ERROR,
        buttons=Gtk.ButtonsType.OK,
        text=title
    )
dialog.format_secondary_text(message)

    if details:
        # Expander für detaillierte Fehlermeldung
        expander = Gtk.Expander(label="Details")
        textview = Gtk.TextView()
        textview.get_buffer().set_text(details)
        textview.set_editable(False)
        expander.add(textview)
        dialog.get_content_area().add(expander)
        dialog.show_all()

    dialog.run()
    dialog.destroy()

# Beispiel-Verwendung
try:
    model = vosk.Model(model_path)
except Exception as e:
    show_error(
        "Vosk Model Load Error",
        "Failed to load speech recognition model.",
        f"Path: {model_path}\nError: {str(e)}\n\nTry downloading the model via Settings."
    )
```

### 7.3 Crash-Recovery
```python
import signal
import sys
import traceback

def handle_crash(exc_type, exc_value, exc_traceback):
    # Log kompletten Stacktrace
    logger.critical("Unhandled exception", exc_info=(exc_type, exc_value, exc_traceback))

    # Zeige Crash-Dialog
    crash_report = ''.join(traceback.format_exception(exc_type, exc_value, exc_traceback))
    show_error(
        "Application Crash",
        "The application encountered an unexpected error.",
        crash_report
    )

    # Versuche Config zu speichern
    try:
        Config.save(current_config)
    except:
        pass

    sys.exit(1)

sys.excepthook = handle_crash

# Signal-Handler für SIGTERM/SIGINT
def signal_handler(sig, frame):
    logger.info(f"Received signal {sig}, shutting down gracefully...")
    # Cleanup
    if terminal_process:
        terminal_process.terminate()
    if audio_stream:
        audio_stream.stop()
    Gtk.main_quit()

signal.signal(signal.SIGINT, signal_handler)
signal.signal(signal.SIGTERM, signal_handler)
```

## 8. Projektstruktur & Dateien

```
codex-stt-assistant/
├── src/
│   ├── __init__.py
│   ├── main.py                 # Entry point, GTK Application setup
│   ├── ui/
│   │   ├── __init__.py
│   │   ├── main_window.py      # Hauptfenster-Klasse
│   │   ├── stt_panel.py        # STT Input Panel Widget
│   │   ├── terminal_panel.py   # Terminal Widget Wrapper
│   │   ├── settings_dialog.py  # Settings-Dialog
│   │   ├── model_downloader.py # Model-Download-UI
│   │   └── shortcuts_dialog.py # Shortcuts Cheatsheet
│   ├── stt/
│   │   ├── __init__.py
│   │   ├── vosk_engine.py      # Vosk STT Implementation
│   │   ├── whisper_engine.py   # Whisper.cpp Integration
│   │   ├── base_engine.py      # Abstract Base Class für STT
│   │   └── audio_capture.py    # PyAudio Wrapper
│   ├── terminal/
│   │   ├── __init__.py
│   │   ├── pty_manager.py      # PTY/VTE Management
│   │   └── input_injector.py   # Text-Input-Injektion
│   ├── config/
│   │   ├── __init__.py
│   │   ├── config_manager.py   # Config Load/Save
│   │   └── defaults.py         # Default-Werte
│   ├── utils/
│   │   ├── __init__.py
│   │   ├── logger.py           # Logging-Setup
│   │   ├── shortcuts.py        # Shortcut-Management
│   │   └── theme.py            # Theme-Switching
│   └── resources/
│       ├── style-dark.css      # Dark-Mode CSS
│       ├── style-light.css     # Light-Mode CSS
│       └── icons/
│           ├── app-icon.svg
│           ├── mic-idle.svg
│           ├── mic-recording.svg
│           └── mic-processing.svg
├── tests/
│   ├── test_stt.py
│   ├── test_terminal.py
│   └── test_config.py
├── data/
│   └── codex-stt-assistant.desktop  # Desktop Entry
├── requirements.txt
├── setup.py
├── pyproject.toml
├── README.md
├── LICENSE
├── INSTALL.md
└── docs/
    ├── screenshots/
    ├── user-guide.md
    └── development.md
```

## 9. Dependencies & Installation

### 9.1 requirements.txt
```
# Core GUI
PyGObject>=3.42.0
pycairo>=1.20.0

# Terminal Emulation
pyte>=0.8.0  # Falls VTE nicht verfügbar, als Fallback

# Audio & STT
pyaudio>=0.2.13
vosk>=0.3.45
webrtcvad>=2.0.10  # Voice Activity Detection

# Utilities
requests>=2.28.0  # Model-Downloads
aiohttp>=3.8.0    # Async Downloads

# Optional
# whisper-cpp-python>=0.2.0  # Falls Whisper gewünscht
```

### 9.2 Systemabhängigkeiten (Ubuntu/Debian)
```bash
# Build-Essentials
sudo apt-get install -y \
    python3-dev \
    python3-pip \
    build-essential \
    pkg-config

# GTK4 & VTE
sudo apt-get install -y \
    libgtk-4-dev \
    libvte-2.91-dev \
    gir1.2-vte-2.91 \
    libgirepository1.0-dev

# Audio
sudo apt-get install -y \
    portaudio19-dev \
    libasound2-dev \
    pulseaudio

# GStreamer (für erweiterte Audio-Features)
sudo apt-get install -y \
    gstreamer1.0-tools \
    gstreamer1.0-plugins-base \
    gstreamer1.0-plugins-good

# Optional: Qt6 falls Qt-Version gewünscht
# sudo apt-get install -y \
#     qt6-base-dev \
#     libqt6core5compat6-dev
```

### 9.3 Installation-Script (install.sh)
```bash
#!/bin/bash
set -e

echo "=== Codex STT Assistant Installer ==="
echo ""

# Check Ubuntu Version
if ! grep -q "Ubuntu" /etc/os-release; then
    echo "Warning: This script is optimized for Ubuntu. Proceed? (y/n)"
    read -r response
    if [[ ! "$response" =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Install system dependencies
echo "Installing system dependencies..."
sudo apt-get update
sudo apt-get install -y \
    python3-dev python3-pip python3-venv \
    libgtk-4-dev libvte-2.91-dev gir1.2-vte-2.91 libgirepository1.0-dev \
    portaudio19-dev libasound2-dev pulseaudio \
    gstreamer1.0-tools gstreamer1.0-plugins-base gstreamer1.0-plugins-good \
    build-essential pkg-config

# Create virtual environment
echo "Creating virtual environment..."
python3 -m venv venv
source venv/bin/activate

# Install Python dependencies
echo "Installing Python packages..."
pip install --upgrade pip
pip install -r requirements.txt

# Create directories
echo "Creating application directories..."
mkdir -p ~/.local/share/codex-stt-assistant/models
mkdir -p ~/.config/codex-stt-assistant

# Download default Vosk model
echo "Downloading German Vosk model (this may take a few minutes)..."
cd ~/.local/share/codex-stt-assistant/models
wget -q --show-progress https://alphacephei.com/vosk/models/vosk-model-de-0.21.zip
unzip -q vosk-model-de-0.21.zip
rm vosk-model-de-0.21.zip
cd -

# Install desktop entry
echo "Installing desktop entry..."
sudo cp data/codex-stt-assistant.desktop /usr/share/applications/
sudo update-desktop-database

# Create launcher script
echo "Creating launcher script..."
cat > ~/.local/bin/codex-stt-assistant << 'EOF'
#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd ../.. && pwd )"
source "$DIR/venv/bin/activate"
python3 "$DIR/src/main.py" "$@"
EOF
chmod +x ~/.local/bin/codex-stt-assistant

echo ""
echo "=== Installation complete! ==="
echo "Run 'codex-stt-assistant' or find it in your application menu."
echo ""
```

### 9.4 Desktop Entry (codex-stt-assistant.desktop)
```ini
[Desktop Entry]
Version=1.0
Type=Application
Name=Codex STT Assistant
GenericName=AI Code Assistant with Speech-to-Text
Comment=Interactive Codex CLI with Speech Recognition
Exec=codex-stt-assistant
Icon=codex-stt-assistant
Terminal=false
Categories=Development;Utility;
Keywords=AI;Codex;STT;Speech;Terminal;
StartupNotify=true
```

## 10. Build & Packaging

### 10.1 setup.py
```python
from setuptools import setup, find_packages

setup(
    name='codex-stt-assistant',
    version='1.0.0',
    description='Speech-to-Text enabled GUI for Codex CLI',
    author='Your Name',
    author_email='your.email@example.com',
    url='https://github.com/yourusername/codex-stt-assistant',
    packages=find_packages(where='src'),
    package_dir={'': 'src'},
    include_package_data=True,
    package_data={
        'codex_stt_assistant': [
            'resources/**/*',
        ]
    },
    install_requires=[
        'PyGObject>=3.42.0',
        'pycairo>=1.20.0',
        'pyaudio>=0.2.13',
        'vosk>=0.3.45',
        'webrtcvad>=2.0.10',
        'requests>=2.28.0',
        'aiohttp>=3.8.0',
    ],
    extras_require={
        'dev': [
            'pytest>=7.0.0',
            'pytest-cov>=3.0.0',
            'black>=22.0.0',
            'flake8>=4.0.0',
            'mypy>=0.950',
        ]
    },
    entry_points={
        'console_scripts': [
            'codex-stt-assistant=codex_stt_assistant.main:main',
        ],
    },
    classifiers=[
        'Development Status :: 4 - Beta',
        'Intended Audience :: Developers',
        'License :: OSI Approved :: MIT License',
        'Programming Language :: Python :: 3',
        'Programming Language :: Python :: 3.9',
        'Programming Language :: Python :: 3.10',
        'Programming Language :: Python :: 3.11',
        'Operating System :: POSIX :: Linux',
        'Topic :: Software Development',
        'Topic :: Utilities',
    ],
    python_requires='>=3.9',
)
```

### 10.2 Debian Package (Optional)
```bash
# Erstelle .deb Paket mit stdeb
pip install stdeb
python3 setup.py --command-packages=stdeb.command bdist_deb

# Oder manuell mit dpkg-deb
mkdir -p debian-package/DEBIAN
mkdir -p debian-package/usr/local/bin
mkdir -p debian-package/usr/share/applications

# Control-File
cat > debian-package/DEBIAN/control << EOF
Package: codex-stt-assistant
Version: 1.0.0
Section: devel
Priority: optional
Architecture: amd64
Depends: python3 (>= 3.9), python3-gi, gir1.2-gtk-4.0, gir1.2-vte-2.91, portaudio19-dev
Maintainer: Your Name <your.email@example.com>
Description: Speech-to-Text GUI for Codex CLI
 Interactive terminal interface for Codex with integrated
 speech recognition using Vosk.
EOF

dpkg-deb --build debian-package codex-stt-assistant_1.0.0_amd64.deb
```

### 10.3 AppImage (Portable)
```bash
# Mit PyInstaller
pip install pyinstaller

pyinstaller --name=codex-stt-assistant \
    --onefile \
    --windowed \
    --add-data "src/resources:resources" \
    --hidden-import=gi \
    --hidden-import=vosk \
    src/main.py

# AppImage-Tool
wget https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage
chmod +x appimagetool-x86_64.AppImage

mkdir -p AppDir/usr/bin
cp dist/codex-stt-assistant AppDir/usr/bin/
cp data/codex-stt-assistant.desktop AppDir/
cp src/resources/icons/app-icon.svg AppDir/codex-stt-assistant.svg

./appimagetool-x86_64.AppImage AppDir codex-stt-assistant-x86_64.AppImage
```

## 11. Testing

### 11.1 Unit Tests
```python
# tests/test_stt.py
import pytest
from unittest.mock import Mock, patch
from codex_stt_assistant.stt.vosk_engine import VoskEngine

def test_vosk_initialization():
    with patch('vosk.Model') as mock_model:
        engine = VoskEngine(model_path='/fake/path')
        mock_model.assert_called_once()

def test_audio_processing():
    engine = VoskEngine(model_path='/fake/path')
    audio_data = b'\x00' * 8000  # Fake audio

    with patch.object(engine.recognizer, 'AcceptWaveform', return_value=True):
        with patch.object(engine.recognizer, 'Result', return_value='{"text":"test"}'):
            result = engine.process_audio(audio_data)
            assert result == "test"

# tests/test_terminal.py
def test_terminal_input_injection():
    from codex_stt_assistant.terminal.input_injector import inject_text

    mock_terminal = Mock()
    inject_text(mock_terminal, "test command")

    mock_terminal.feed_child.assert_called()
    # Verify encoded bytes and newline
    calls = mock_terminal.feed_child.call_args_list
    assert calls[0][0][0] == b'test command'
    assert calls[1][0][0] == b'\n'

# tests/test_config.py
def test_config_load_save():
    from codex_stt_assistant.config.config_manager import Config
    import tempfile

    with tempfile.NamedTemporaryFile(mode='w', delete=False, suffix='.json') as f:
        Config.CONFIG_FILE = f.name

        # Test save
        test_config = {'ui': {'theme': 'dark'}}
        Config.save(test_config)

        # Test load
        loaded = Config.load()
        assert loaded['ui']['theme'] == 'dark'
```

### 11.2 Integration Tests
```python
# tests/test_integration.py
import pytest
from gi.repository import Gtk, GLib
from codex_stt_assistant.ui.main_window import MainWindow

@pytest.fixture
def app():
    app = Gtk.Application()
    return app

def test_main_window_creation(app):
    window = MainWindow(app)
    assert window is not None
    assert window.stt_panel is not None
    assert window.terminal_panel is not None

def test_stt_to_terminal_flow(app):
    window = MainWindow(app)

    # Simuliere STT-Eingabe
    test_text = "echo 'hello world'"
    window.stt_panel.on_stt_result(test_text)

    # Warte auf UI-Update
    while Gtk.events_pending():
        Gtk.main_iteration()

    # Prüfe ob Text im Terminal ankam
    # (requires mock oder real terminal)
    assert window.terminal_panel.last_input == test_text
```

### 11.3 Test-Ausführung
```bash
# Alle Tests
pytest tests/ -v

# Mit Coverage
pytest tests/ --cov=src/codex_stt_assistant --cov-report=html

# Nur STT-Tests
pytest tests/test_stt.py -v

# Mit Logging
pytest tests/ -v --log-cli-level=DEBUG
```

## 12. Dokumentation

### 12.1 README.md Structure
```markdown
# Codex STT Assistant

> Speech-to-Text enabled GUI for Codex CLI on Linux

[Screenshot]

## Features
- 🎤 Voice-controlled Codex interaction
- 💬 Real-time speech recognition (offline)
- 🖥️ Full terminal emulation
- 🌓 Dark/Light themes
- ⌨️ Extensive keyboard shortcuts
- 📝 Input history

## Quick Start
[Installation instructions]
[Usage examples]
[Screenshots]

## Requirements
[System requirements]
[Dependencies]

## Configuration
[Config file location]
[Available options]

## Troubleshooting
[Common issues]
[FAQ]

## Development
[Build from source]
[Contributing]

## License
MIT
```

### 12.2 User Guide (docs/user-guide.md)
- Getting Started
- Basic Usage
- Speech Recognition Tips
- Keyboard Shortcuts Reference
- Configuration Guide
- Advanced Features
- Troubleshooting

### 12.3 API Documentation
```bash
# Generate API docs mit Sphinx
pip install sphinx sphinx-rtd-theme

cd docs
sphinx-quickstart
# Configure for autodoc

# In conf.py:
extensions = ['sphinx.ext.autodoc', 'sphinx.ext.napoleon']

# Generate
make html
```

## 13. Performance-Optimierungen

### 13.1 Lazy Loading
```python
# Lade schwere Module nur bei Bedarf
class STTManager:
    def __init__(self):
        self._vosk = None
        self._whisper = None

    @property
    def vosk(self):
        if self._vosk is None:
            import vosk
            self._vosk = vosk
        return self._vosk
```

### 13.2 Audio-Buffer-Optimierung
```python
# Ring-Buffer für Audio-Daten
from collections import deque

class AudioBuffer:
    def __init__(self, maxsize=100):
        self.buffer = deque(maxlen=maxsize)

    def add(self, chunk):
        self.buffer.append(chunk)

    def get_all(self):
        return b''.join(self.buffer)

    def clear(self):
        self.buffer.clear()
```

### 13.3 Terminal-Output-Optimierung
```python
# Batch terminal updates
import time

class TerminalOutputBatcher:
    def __init__(self, terminal, batch_interval=0.016):  # ~60 FPS
        self.terminal = terminal
        self.batch_interval = batch_interval
        self.pending_output = []
        self.last_flush = time.time()

    def add_output(self, text):
        self.pending_output.append(text)

        if time.time() - self.last_flush > self.batch_interval:
            self.flush()

    def flush(self):
        if self.pending_output:
            combined = ''.join(self.pending_output)
            self.terminal.feed(combined.encode())
            self.pending_output.clear()
            self.last_flush = time.time()
```

## 14. Sicherheit & Datenschutz

### 14.1 Lokale Verarbeitung
- Alle STT-Verarbeitung erfolgt lokal (Vosk)
- Keine Cloud-APIs im Default-Setup
- Audio-Daten werden nicht gespeichert (nur in RAM)

### 14.2 Credentials-Handling
```python
# Falls Custom API verwendet wird
import keyring

def save_api_key(service, username, password):
    keyring.set_password(service, username, password)

def load_api_key(service, username):
    return keyring.get_password(service, username)

# Niemals API-Keys in Config-File!
```

### 14.3 Sandbox-Option
```python
# Optional: Codex in Container/Sandbox starten
import subprocess

def start_codex_sandboxed():
    # Mit firejail oder bubblewrap
    cmd = [
        'firejail',
        '--noprofile',
        '--private-tmp',
        '--net=none',  # Kein Netzwerk
        '/usr/bin/codex'
    ]
    return subprocess.Popen(cmd, ...)
```

## 15. Zusätzliche Features (Optional/Zukunft)

### 15.1 Multi-Language-Support
- i18n via gettext
- Übersetzbare UI-Strings
- Sprach-Auswahl im Settings

### 15.2 Plugin-System
```python
# Plugin-Interface
class Plugin:
    def on_stt_result(self, text):
        """Called when STT produces result"""
        pass

    def on_terminal_output(self, output):
        """Called when terminal outputs text"""
        pass

    def get_ui_widget(self):
        """Return GTK widget for plugin settings"""
        return None

# Plugin-Loader
import importlib
import pkgutil

def load_plugins():
    plugins = []
    for _, name, _ in pkgutil.iter_modules(['plugins']):
        module = importlib.import_module(f'plugins.{name}')
        if hasattr(module, 'Plugin'):
            plugins.append(module.Plugin())
    return plugins
```

### 15.3 Cloud-Sync (Optional)
- History-Sync via Nextcloud/Git
- Config-Sync über Cloud-Storage
- Opt-in Feature, default disabled

### 15.4 LLM-Integration
- Optionale Claude/GPT Integration für Code-Suggestions
- Pre-Processing von STT-Input vor Codex
- Post-Processing von Codex-Output

## 16. Deliverables - Checkliste

Stelle sicher, dass die finale Anwendung folgendes enthält:

- [x] Vollständiger Source Code in modularer Struktur
- [x] requirements.txt mit allen Python-Dependencies
- [x] install.sh Script für Ubuntu-Installation
- [x] .desktop File für Desktop-Integration
- [x] README.md mit Quick-Start-Guide
- [x] INSTALL.md mit detaillierter Installations-Anleitung
- [x] User-Guide Documentation
- [x] Konfigurationsfile mit Defaults
- [x] Unit-Tests für Core-Komponenten
- [x] Screenshots und Demo-GIFs
- [x] LICENSE File (z.B. MIT)
- [x] CHANGELOG.md für Version-History

## 17. Finales Deployment

### 17.1 Release-Checklist
1. Alle Tests laufen erfolgreich
2. Dokumentation ist vollständig
3. Version-Nummer in allen Files aktualisiert
4. CHANGELOG.md aktualisiert
5. Screenshots aktuell
6. Build auf cleanem Ubuntu 22.04/24.04 getestet
7. Memory-Leaks geprüft (valgrind/heaptrack)
8. Performance-Profiling durchgeführt

### 17.2 Distribution
```bash
# GitHub Release
git tag v1.0.0
git push origin v1.0.0

# Create release assets
./build-release.sh  # Erstellt .deb, AppImage, source tarball

# Upload to GitHub Releases
gh release create v1.0.0 \
    codex-stt-assistant_1.0.0_amd64.deb \
    codex-stt-assistant-x86_64.AppImage \
    codex-stt-assistant-1.0.0.tar.gz \
    --title "Codex STT Assistant v1.0.0" \
    --notes "Initial release"
```

---

## Zusammenfassung

Dieses Tool soll eine **produktionsreife, native Linux-Anwendung** sein mit:

1. **Moderne GTK4/Qt6 GUI** - Sauberes, responsives Design
2. **Robuste STT-Integration** - Offline Vosk mit deutscher Sprache
3. **Echte Terminal-Emulation** - Keine Mock-Terminals, echtes PTY
4. **Professionelles UX** - Keyboard-Shortcuts, Themes, History
5. **Einfache Installation** - One-Command-Install für Ubuntu
6. **Wartbar & Erweiterbar** - Saubere Architektur, Tests, Docs

Die Anwendung muss **sofort nach Installation funktionieren** und dem User das Gefühl eines professionellen Tools geben, nicht eines Prototyps.

**Beginne mit der Implementierung und halte dich an alle technischen Spezifikationen!**
```
