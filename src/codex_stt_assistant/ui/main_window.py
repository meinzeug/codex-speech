from gi.repository import Gtk, GLib

from ..config import Config
from ..stt import create_engine
from ..utils import apply_theme, HistoryStore
from .stt_panel import STTPanel
from .terminal_panel import TerminalPanel
from .settings_dialog import SettingsDialog
from .shortcuts_dialog import ShortcutsDialog
from .dialogs import show_error


class MainWindow(Gtk.ApplicationWindow):
    def __init__(self, app, config):
        super().__init__(application=app, title="Codex STT Assistant")
        self.config = config
        self.add_css_class("app-window")

        self.set_default_size(
            self.config.get('ui', {}).get('window', {}).get('width', 1400),
            self.config.get('ui', {}).get('window', {}).get('height', 800),
        )

        self.history_store = HistoryStore(max_items=self.config.get('ui', {}).get('history_size', 50))
        self.stt_engine = create_engine(
            self.config,
            on_result=self._on_stt_result,
            on_partial=self._on_stt_partial,
            on_status=self._on_stt_status,
            on_level=self._on_stt_level,
        )

        self._build_header()
        self._build_layout()
        self._build_statusbar()

        apply_theme(self.config.get('ui', {}).get('theme', 'dark'), self.get_display())

    def _build_header(self):
        header = Gtk.HeaderBar()
        header.add_css_class("app-header")

        theme_button = Gtk.Button(label="Theme")
        theme_button.add_css_class("ghost-button")
        theme_button.connect("clicked", self._on_theme_toggle)

        settings_button = Gtk.Button(label="Settings")
        settings_button.add_css_class("ghost-button")
        settings_button.connect("clicked", self._on_settings)

        shortcuts_button = Gtk.Button(label="Shortcuts")
        shortcuts_button.add_css_class("ghost-button")
        shortcuts_button.connect("clicked", self._on_shortcuts)

        header.pack_start(theme_button)
        header.pack_end(shortcuts_button)
        header.pack_end(settings_button)

        self.set_titlebar(header)

    def _build_layout(self):
        paned = Gtk.Paned(orientation=Gtk.Orientation.HORIZONTAL)
        paned.add_css_class("main-split")
        window_cfg = self.config.get('ui', {}).get('window', {})
        width = window_cfg.get('width', 1400)
        ratio = window_cfg.get('splitter_position', 0.3)
        paned.set_position(int(width * ratio))

        self.stt_panel = STTPanel(
            config=self.config,
            history_store=self.history_store,
            on_send=self._on_send,
            on_record_start=self._on_record_start,
            on_record_stop=self._on_record_stop,
            on_device_change=self._on_device_change,
        )

        self.terminal_panel = TerminalPanel(self.config)

        paned.set_start_child(self.stt_panel)
        paned.set_end_child(self.terminal_panel)
        self._paned = paned

    def _build_statusbar(self):
        self.status_label = Gtk.Label(label="Ready")
        self.status_label.set_xalign(0.0)
        self.status_bar = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        self.status_bar.set_margin_start(12)
        self.status_bar.set_margin_end(12)
        self.status_bar.set_margin_bottom(8)
        self.status_bar.set_halign(Gtk.Align.FILL)
        self.status_bar.set_valign(Gtk.Align.END)
        self.status_bar.add_css_class("status-bar")
        self.status_bar.append(self.status_label)

        overlay = Gtk.Overlay()
        overlay.set_child(self._paned)
        overlay.add_overlay(self.status_bar)
        self.set_child(overlay)

    def _on_send(self, text):
        self.terminal_panel.send_text(text)
        self._set_status("Sent input to Codex")

    def _on_record_start(self):
        try:
            self.stt_engine.start()
        except Exception as exc:
            show_error(self, "STT Error", "Failed to start recording.", str(exc))

    def _on_record_stop(self):
        self.stt_engine.stop()

    def _on_device_change(self, device):
        try:
            self.stt_engine.stop()
        except Exception:
            pass
        self.stt_engine = create_engine(
            self.config,
            on_result=self._on_stt_result,
            on_partial=self._on_stt_partial,
            on_status=self._on_stt_status,
            on_level=self._on_stt_level,
        )
        name = device.get('name') if device else 'Default'
        self._set_status(f"Mic set to {name}")

    def _on_stt_result(self, text):
        if self.stt_panel.auto_submit_enabled():
            GLib.idle_add(self.stt_panel.add_history, text)
            self._on_send(text)
            GLib.idle_add(self.stt_panel.set_text, "")
        else:
            GLib.idle_add(self.stt_panel.set_text, text)

    def _on_stt_partial(self, text):
        GLib.idle_add(self.stt_panel.set_partial, text)

    def _on_stt_status(self, status):
        GLib.idle_add(self.stt_panel.set_status, status)
        GLib.idle_add(self._set_status, f"STT: {status}")

    def _on_stt_level(self, level):
        GLib.idle_add(self.stt_panel.set_level, level)

    def _on_settings(self, _button):
        SettingsDialog(self, self.config, self._apply_settings)

    def _on_shortcuts(self, _button):
        ShortcutsDialog(self)

    def _on_theme_toggle(self, _button):
        current = self.config.get('ui', {}).get('theme', 'dark')
        new = 'light' if current == 'dark' else 'dark'
        self.config['ui']['theme'] = new
        apply_theme(new, self.get_display())
        Config.save(self.config)

    def _apply_settings(self, config):
        Config.save(config)
        self.stt_panel.auto_submit.set_active(config.get('ui', {}).get('auto_submit', True))
        self.history_store.max_items = config.get('ui', {}).get('history_size', 50)
        apply_theme(config.get('ui', {}).get('theme', 'dark'), self.get_display())
        try:
            self.stt_engine.stop()
        except Exception:
            pass
        self.stt_engine = create_engine(
            config,
            on_result=self._on_stt_result,
            on_partial=self._on_stt_partial,
            on_status=self._on_stt_status,
            on_level=self._on_stt_level,
        )
        self._set_status("Settings updated. Restart may be required for some changes.")

    def _set_status(self, text):
        self.status_label.set_text(text)

    def run_self_test(self, app):
        def _send_test():
            self.stt_panel.set_text("Self-test message")
            self.stt_panel.submit_text()
            return False

        def _record_test():
            try:
                self._on_record_start()
            except Exception:
                return False
            return False

        def _stop_record():
            try:
                self._on_record_stop()
            except Exception:
                pass
            return False

        def _quit():
            app.quit()
            return False

        GLib.timeout_add(500, _send_test)
        GLib.timeout_add(800, _record_test)
        GLib.timeout_add(1600, _stop_record)
        GLib.timeout_add(2200, _quit)
