from datetime import datetime
from pathlib import Path
import os

from gi.repository import Gtk, GLib

from ..terminal import PTYManager
from .dialogs import show_error, show_info


class TerminalPanel(Gtk.Box):
    def __init__(self, config):
        super().__init__(orientation=Gtk.Orientation.VERTICAL, spacing=6)
        self.add_css_class("terminal-panel")
        self.set_margin_top(6)
        self.set_margin_bottom(6)
        self.set_margin_start(6)
        self.set_margin_end(6)

        self.config = config
        self.pty = None

        self._build_toolbar()
        if os.environ.get("CODEX_STT_NO_TERMINAL") == "1":
            note = Gtk.Label(label="Terminal disabled (self-test mode).")
            note.set_xalign(0.0)
            note.add_css_class("muted-label")
            self.append(note)
            return
        try:
            self.pty = PTYManager(config, on_exit=self._on_terminal_exit)
            self.pty.terminal.add_css_class("terminal-view")
            self.append(self.pty.terminal)
            try:
                self.pty.spawn()
            except Exception as exc:
                show_error(self.get_root(), "Codex Error", "Failed to start Codex.", str(exc))
                raise
        except Exception as exc:
            error = Gtk.Label(label=f"Terminal unavailable: {exc}")
            error.set_wrap(True)
            error.set_xalign(0.0)
            self.append(error)

    def _build_toolbar(self):
        bar = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        bar.add_css_class("terminal-toolbar")

        self.search_entry = Gtk.SearchEntry()
        self.search_entry.set_placeholder_text("Search")
        self.search_entry.add_css_class("search-entry")
        self.search_entry.connect("activate", self._on_search)

        search_next = Gtk.Button(label="Next")
        search_next.add_css_class("ghost-button")
        search_next.connect("clicked", self._on_search)

        save_button = Gtk.Button(label="Save Session")
        save_button.add_css_class("ghost-button")
        save_button.connect("clicked", self._on_save_session)

        self.auto_scroll = Gtk.CheckButton(label="Auto-scroll")
        self.auto_scroll.set_active(True)
        self.auto_scroll.add_css_class("toggle")
        self.auto_scroll.connect("toggled", self._on_auto_scroll_toggled)

        bar.append(self.search_entry)
        bar.append(search_next)
        bar.append(self.auto_scroll)
        bar.append(save_button)
        self.append(bar)

    def send_text(self, text):
        if not self.pty:
            show_error(self.get_root(), "Terminal Error", "Terminal is not available.")
            return
        self.pty.send_text(text)

    def send_partial(self, text):
        if self.pty:
            self.pty.send_raw(text)

    def _on_search(self, _widget):
        if not self.pty:
            return
        term = self.search_entry.get_text().strip()
        if not term:
            return
        try:
            regex = GLib.Regex.new(term, GLib.RegexCompileFlags.CASELESS, 0)
            self.pty.terminal.search_set_gregex(regex, 0)
            self.pty.terminal.search_find_next()
        except Exception as exc:
            show_error(self.get_root(), "Search Error", "Failed to search terminal.", str(exc))

    def _on_auto_scroll_toggled(self, _button):
        if self.pty:
            self.pty.terminal.set_scroll_on_output(self.auto_scroll.get_active())

    def _on_save_session(self, _button):
        if not self.pty:
            return
        try:
            text = self.pty.terminal.get_text_range(0, 0, -1, -1, lambda *args: True)[0]
        except Exception as exc:
            show_error(self.get_root(), "Save Error", "Failed to capture terminal output.", str(exc))
            return

        filename = f"codex_session_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"
        target = Path.home() / filename
        try:
            target.write_text(text, encoding='utf-8')
            show_info(self.get_root(), "Session Saved", f"Saved to {target}")
        except Exception as exc:
            show_error(self.get_root(), "Save Error", "Failed to write session file.", str(exc))

    def _on_terminal_exit(self, status):
        if status != 0:
            show_error(self.get_root(), "Codex Exited", f"Codex exited with status {status}.")
            auto_restart = self.config.get('terminal', {}).get('auto_restart_on_crash', True)
            if auto_restart:
                GLib.timeout_add(1000, self._restart)
        else:
            show_info(self.get_root(), "Codex Session", "Codex session ended.")

    def _restart(self):
        self.pty.reset()
        self.pty.spawn()
        return False
