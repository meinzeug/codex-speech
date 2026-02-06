from pathlib import Path
import os
import threading
import time
import difflib

from gi.repository import Gtk, Gdk, GLib, Pango, Gio

from ..config import Config
from ..stt import list_input_devices


IGNORE_DIRS = {'.git', 'node_modules', 'venv', '__pycache__', '.mypy_cache', '.ruff_cache', '.pytest_cache'}
MAX_SEARCH_RESULTS = 200
MAX_FILE_SIZE = 1_000_000  # 1 MB


class STTPanel(Gtk.Box):
    def __init__(self, config, history_store, on_send, on_record_start, on_record_stop, on_device_change=None):
        super().__init__(orientation=Gtk.Orientation.VERTICAL, spacing=12)
        self.add_css_class("stt-panel")
        self.set_margin_top(12)
        self.set_margin_bottom(12)
        self.set_margin_start(12)
        self.set_margin_end(12)

        self.config = config
        self.history_store = history_store
        self.on_send = on_send
        self.on_record_start = on_record_start
        self.on_record_stop = on_record_stop
        self.on_device_change = on_device_change
        self._recording = False

        self.project_root = self._resolve_project_root()
        self.current_file = None
        self._scan_thread = None
        self._file_entries = []
        self._file_rows = {}
        self._open_files = {}
        self._page_paths = {}
        self._mic_devices = []
        self._mic_populating = False

        self._build_layout()
        self._wire_shortcuts()
        self._reload_history()
        self._refresh_file_list()

    def _resolve_project_root(self):
        env_root = os.environ.get('CODEX_STT_PROJECT_ROOT')
        if env_root:
            root = Path(env_root).expanduser()
            if root.exists():
                return root
        root = Path(self.config.get('project', {}).get('root', '~')).expanduser()
        if not root.exists():
            root = Path.cwd()
        if root == Path.home() and Path.cwd() != Path.home():
            root = Path.cwd()
        return root

    def _build_layout(self):
        self.paned = Gtk.Paned(orientation=Gtk.Orientation.VERTICAL)
        self.paned.add_css_class("left-paned")
        self.paned.set_hexpand(True)
        self.paned.set_vexpand(True)
        self.paned.set_resize_start_child(True)
        self.paned.set_resize_end_child(True)
        self.paned.set_shrink_start_child(True)
        self.paned.set_shrink_end_child(True)

        self.project_panel = self._build_project_panel()
        self.project_panel.set_hexpand(True)
        self.project_panel.set_vexpand(True)
        self.chat_panel = self._build_chat_panel()
        self.chat_panel.set_hexpand(True)
        self.chat_panel.set_vexpand(True)

        self.paned.set_start_child(self.project_panel)
        self.paned.set_end_child(self.chat_panel)
        self.append(self.paned)
        self._apply_left_split()

    def _apply_left_split(self):
        height = int(self.config.get('ui', {}).get('window', {}).get('height', 800))
        min_top = 260
        min_bottom = 240
        pos = int(height * 0.6)
        pos = max(min_top, min(pos, max(min_top, height - min_bottom)))
        self.paned.set_position(pos)

    def _build_project_panel(self):
        panel = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)
        panel.add_css_class("project-panel")

        title_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        title_row.add_css_class("project-header")

        title = Gtk.Label(label="Project")
        title.add_css_class("section-title")
        title.set_xalign(0.0)

        open_button = _icon_button("folder-open-symbolic", "Open project", "ghost-button")
        open_button.connect("clicked", self._on_open_project)

        refresh_button = _icon_button("view-refresh-symbolic", "Refresh files", "ghost-button")
        refresh_button.connect("clicked", self._on_refresh_files)

        new_button = _icon_button("document-new-symbolic", "New file", "ghost-button")
        new_button.connect("clicked", self._on_new_file)

        spacer = Gtk.Box()
        spacer.set_hexpand(True)

        title_row.append(title)
        title_row.append(spacer)
        title_row.append(open_button)
        title_row.append(refresh_button)
        title_row.append(new_button)

        panel.append(title_row)

        path_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        self.project_path_label = Gtk.Label(label=str(self.project_root))
        self.project_path_label.add_css_class("muted-label")
        self.project_path_label.set_xalign(0.0)
        self.project_path_label.set_ellipsize(Pango.EllipsizeMode.MIDDLE)
        path_row.append(self.project_path_label)
        panel.append(path_row)

        search_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        search_row.add_css_class("project-search")

        self.file_search = Gtk.SearchEntry()
        self.file_search.set_placeholder_text("Filter files")
        self.file_search.connect("search-changed", self._on_file_search_changed)

        self.show_hidden = Gtk.CheckButton(label="Hidden")
        self.show_hidden.connect("toggled", self._on_file_search_changed)

        search_button = Gtk.Button(label="Search in files")
        search_button.add_css_class("ghost-button")
        search_button.connect("clicked", self._on_search_files)

        self.file_count = Gtk.Label(label="0 files")
        self.file_count.add_css_class("muted-label")

        search_row.append(self.file_search)
        search_row.append(self.show_hidden)
        search_row.append(search_button)
        search_row.append(self.file_count)

        panel.append(search_row)

        self.search_revealer = Gtk.Revealer()
        self.search_revealer.set_transition_type(Gtk.RevealerTransitionType.SLIDE_DOWN)
        self.search_revealer.set_reveal_child(False)

        results_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        results_box.add_css_class("search-results")

        results_header = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        results_title = Gtk.Label(label="Search results")
        results_title.add_css_class("section-title")
        results_title.set_xalign(0.0)

        clear_results = _icon_button("window-close-symbolic", "Clear results", "ghost-button")
        clear_results.connect("clicked", self._on_clear_results)

        results_header.append(results_title)
        results_header.append(Gtk.Box(hexpand=True))
        results_header.append(clear_results)

        results_box.append(results_header)

        self.search_results = Gtk.ListBox()
        self.search_results.set_selection_mode(Gtk.SelectionMode.SINGLE)
        self.search_results.connect("row-activated", self._on_result_activated)

        results_scroll = Gtk.ScrolledWindow()
        results_scroll.set_child(self.search_results)
        results_scroll.set_policy(Gtk.PolicyType.AUTOMATIC, Gtk.PolicyType.AUTOMATIC)
        results_scroll.set_size_request(-1, 120)

        results_box.append(results_scroll)
        self.search_revealer.set_child(results_box)
        panel.append(self.search_revealer)

        self.file_list = Gtk.ListBox()
        self.file_list.add_css_class("file-list")
        self.file_list.set_selection_mode(Gtk.SelectionMode.SINGLE)
        self.file_list.connect("row-activated", self._on_file_activated)

        file_scroll = Gtk.ScrolledWindow()
        file_scroll.set_child(self.file_list)
        file_scroll.set_vexpand(True)
        file_scroll.set_policy(Gtk.PolicyType.AUTOMATIC, Gtk.PolicyType.AUTOMATIC)

        panel.append(file_scroll)

        editor_header = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        editor_header.add_css_class("editor-header")

        self.file_label = Gtk.Label(label="No file open")
        self.file_label.add_css_class("muted-label")
        self.file_label.set_xalign(0.0)
        self.file_label.set_ellipsize(Pango.EllipsizeMode.MIDDLE)

        save_button = _icon_button("document-save-symbolic", "Save", "primary-button")
        save_button.connect("clicked", self._on_save_file)

        diff_button = _icon_button("view-list-symbolic", "Diff", "ghost-button")
        diff_button.connect("clicked", self._on_diff_file)

        send_button = _icon_button("mail-send-symbolic", "Send selection to Codex", "ghost-button")
        send_button.connect("clicked", self._on_send_selection)

        editor_header.append(self.file_label)
        editor_header.append(save_button)
        editor_header.append(diff_button)
        editor_header.append(send_button)

        panel.append(editor_header)

        self.editor_tabs = Gtk.Notebook()
        self.editor_tabs.add_css_class("editor-tabs")
        self.editor_tabs.set_scrollable(True)
        self.editor_tabs.connect("switch-page", self._on_tab_switched)

        panel.append(self.editor_tabs)
        return panel

    def _build_chat_panel(self):
        panel = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)
        panel.add_css_class("chat-panel")

        self.chat_list = Gtk.ListBox()
        self.chat_list.add_css_class("chat-list")
        self.chat_list.set_selection_mode(Gtk.SelectionMode.NONE)

        self.chat_scroll = Gtk.ScrolledWindow()
        self.chat_scroll.set_child(self.chat_list)
        self.chat_scroll.set_vexpand(True)
        self.chat_scroll.set_policy(Gtk.PolicyType.AUTOMATIC, Gtk.PolicyType.AUTOMATIC)

        panel.append(self.chat_scroll)

        self.composer = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=6)
        self.composer.add_css_class("composer")

        # Row 1: Audio Controls (Top)
        audio_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        audio_row.add_css_class("composer-row")

        mic_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        mic_label = Gtk.Label(label="Mic")
        mic_label.add_css_class("muted-label")
        mic_label.set_xalign(0.0)
        self.mic_combo = Gtk.ComboBoxText()
        self.mic_combo.add_css_class("mic-select")
        # Enable ellipsization for long device names
        for cell in self.mic_combo.get_cells():
            if isinstance(cell, Gtk.CellRendererText):
                cell.set_property("ellipsize", Pango.EllipsizeMode.END)
                cell.set_property("width-chars", 10)  # Minimum visible chars
                cell.set_property("max-width-chars", 25)
        self.mic_combo.connect("changed", self._on_mic_changed)
        refresh_mic = _icon_button("view-refresh-symbolic", "Refresh microphones", "ghost-button")
        refresh_mic.connect("clicked", self._on_mic_refresh)
        mic_box.append(mic_label)
        mic_box.append(self.mic_combo)
        mic_box.append(refresh_mic)

        self.record_button = _icon_button("media-record-symbolic", "Start recording", "primary-button")
        self.record_button.connect("clicked", self._on_record_clicked)

        self.live_button = _icon_button("network-wireless-symbolic", "Live Mode", "danger-button") # Using danger/red for live
        self.live_button.connect("clicked", self._on_live_clicked)

        self.stop_button = _icon_button("media-playback-stop-symbolic", "Stop recording", "danger-button")
        self.stop_button.set_visible(False)
        self.stop_button.connect("clicked", self._on_stop_clicked)

        self.level_bar = Gtk.LevelBar()
        self.level_bar.set_min_value(0.0)
        self.level_bar.set_max_value(1.0)
        self.level_bar.set_value(0.0)
        self.level_bar.add_css_class("level-meter")
        self.level_bar.set_hexpand(True)

        audio_row.append(mic_box)
        audio_row.append(self.record_button)
        audio_row.append(self.live_button)
        audio_row.append(self.stop_button)
        audio_row.append(self.level_bar)

        self.composer.append(audio_row)

        # Message Input Area (Horizontal Layout)
        input_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        
        msg_label = Gtk.Label(label="Message:")
        msg_label.set_valign(Gtk.Align.START)
        msg_label.set_margin_top(8) # Align with text
        msg_label.add_css_class("muted-label")
        input_row.append(msg_label)

        overlay = Gtk.Overlay()
        self.text_view = Gtk.TextView()
        self.text_view.add_css_class("input-area")
        self.text_view.set_wrap_mode(Gtk.WrapMode.WORD_CHAR)
        self.text_view.set_hexpand(True)
        self.text_view.set_vexpand(False)
        # self.text_view.set_size_request(-1, 80) # Let CSS handle min-height

        self.text_buffer = self.text_view.get_buffer()
        self.text_buffer.connect("changed", self._on_buffer_changed)

        self.placeholder = Gtk.Label(label="Type a message…")
        self.placeholder.set_xalign(0.0)
        self.placeholder.set_margin_top(8) # Match padding
        self.placeholder.set_margin_start(8)
        self.placeholder.set_opacity(0.4)
        self.placeholder.add_css_class("placeholder")

        overlay.set_child(self.text_view)
        overlay.add_overlay(self.placeholder)

        scroll = Gtk.ScrolledWindow()
        scroll.set_child(overlay)
        scroll.set_policy(Gtk.PolicyType.AUTOMATIC, Gtk.PolicyType.AUTOMATIC)
        scroll.set_hexpand(True)
        scroll.set_propagate_natural_height(True)
        scroll.set_max_content_height(150) # Allow growing but limit max height

        input_row.append(scroll)
        self.composer.append(input_row)

        # Row 2: Action Controls (Bottom)
        action_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        action_row.add_css_class("composer-row")

        self.auto_submit = Gtk.CheckButton(label="Auto-submit")
        self.auto_submit.set_active(self.config.get('ui', {}).get('auto_submit', True))
        self.auto_submit.add_css_class("toggle")

        self.send_button = _icon_button("mail-send-symbolic", "Send message", "primary-button")
        self.send_button.connect("clicked", self._on_send_clicked)

        self.clear_button = _icon_button("edit-clear-symbolic", "Clear input", "ghost-button")
        self.clear_button.connect("clicked", self._on_clear_clicked)

        self.char_count = Gtk.Label(label="0")
        self.char_count.set_xalign(1.0)
        self.char_count.add_css_class("muted-label")

        spacer = Gtk.Box()
        spacer.set_hexpand(True)

        action_row.append(self.auto_submit)
        action_row.append(spacer)
        action_row.append(self.clear_button)
        action_row.append(self.char_count)
        action_row.append(self.send_button)

        self.composer.append(action_row)
        self.partial_label = Gtk.Label(label="")
        self.partial_label.set_xalign(0.0)
        self.partial_label.add_css_class("muted-label")
        self.composer.append(self.partial_label)

        panel.append(self.composer)
        self._refresh_mic_devices()
        return panel

    def _wire_shortcuts(self):
        controller = Gtk.EventControllerKey()
        controller.connect("key-pressed", self._on_key_pressed)
        self.add_controller(controller)

    def _on_key_pressed(self, _controller, keyval, keycode, state):
        ctrl = state & Gdk.ModifierType.CONTROL_MASK
        shift = state & Gdk.ModifierType.SHIFT_MASK
        if keyval == Gdk.KEY_Return and not ctrl and not shift:
            self._on_send_clicked(None)
            return True
        if keyval == Gdk.KEY_Return and ctrl:
            self._on_send_clicked(None)
            return True
        if ctrl and keyval == Gdk.KEY_l:
            self._on_clear_clicked(None)
            return True
        if ctrl and keyval == Gdk.KEY_s:
            self._on_save_file(None)
            return True
        if ctrl and keyval == Gdk.KEY_space:
            if self._recording:
                self._on_stop_clicked(None)
            else:
                self._on_record_clicked(None)
            return True
        return False

    def _on_record_clicked(self, _button):
        if self._recording:
            return
        self._recording = True
        self._live_mode = False
        self.record_button.set_visible(False)
        self.live_button.set_visible(False)
        self.stop_button.set_visible(True)
        if self.on_record_start:
            self.on_record_start()

    def _on_live_clicked(self, _button):
        print("DEBUG: Live clicked")
        if self._recording:
            return
        self._recording = True
        self._live_mode = True
        self.record_button.set_visible(False)
        self.live_button.set_visible(False)
        self.stop_button.set_visible(True)
        if self.on_record_start:
            self.on_record_start()

    def _on_stop_clicked(self, _button):
        if not self._recording:
            return
        self._recording = False
        # self._live_mode = False # Don't reset immediately, let status idle reset it? 
        # Actually proper reset is fine, but let's debug.
        # self.record_button.set_visible(True)
        # self.stop_button.set_visible(False)
        if self.on_record_stop:
            self.on_record_stop()

    def _on_send_clicked(self, _button):
        text = self.get_text().strip()
        if not text:
            return
        if self.on_send:
            self.on_send(text)
        self._add_history(text)
        self.set_text("")

    def submit_text(self):
        self._on_send_clicked(None)

    def _on_clear_clicked(self, _button):
        self.set_text("")
        self.partial_label.set_text("")

    def _on_buffer_changed(self, _buffer):
        text = self.get_text()
        self.char_count.set_text(str(len(text)))
        self.placeholder.set_visible(len(text) == 0)

    def set_partial(self, text):
        self.partial_label.set_text(text)

    def is_live_mode(self):
        val = getattr(self, '_live_mode', False)
        # print(f"DEBUG: is_live_mode? {val}")
        return val

    def set_status(self, status):
        if status.startswith('error:'):
            self.partial_label.set_text(status)
            self._recording = False
            self.record_button.set_visible(True)
            self.live_button.set_visible(True)
            self.stop_button.set_visible(False)
        elif status in ('recording', 'loading', 'transcribing'):
            self.partial_label.set_text(status)
            if status == 'recording':
                self._recording = True
                self.record_button.set_visible(False)
                self.live_button.set_visible(False)
                self.stop_button.set_visible(True)
        elif status == 'idle':
            self.partial_label.set_text("")
            self._recording = False
            self.record_button.set_visible(True)
            self.live_button.set_visible(True)
            self.stop_button.set_visible(False)

    def set_level(self, level):
        try:
            self.level_bar.set_value(max(0.0, min(1.0, float(level))))
        except Exception:
            pass

    def _on_mic_refresh(self, _button):
        self._refresh_mic_devices()

    def _refresh_mic_devices(self):
        self._mic_populating = True
        self.mic_combo.remove_all()
        self._mic_devices = list_input_devices()
        self.mic_combo.append_text("Default")
        for dev in self._mic_devices:
            label = f"{dev['name']} ({dev['rate']} Hz)"
            self.mic_combo.append_text(label)

        selected = 0
        cfg = self.config.get('audio', {})
        cfg_index = cfg.get('device_index')
        cfg_name = cfg.get('device_name')
        if cfg_index is not None:
            for idx, dev in enumerate(self._mic_devices):
                if dev['index'] == cfg_index:
                    selected = idx + 1
                    break
        elif cfg_name:
            for idx, dev in enumerate(self._mic_devices):
                if dev['name'] == cfg_name:
                    selected = idx + 1
                    break
        self.mic_combo.set_active(selected)
        self._mic_populating = False

    def _on_mic_changed(self, _combo):
        if self._mic_populating:
            return
        idx = self.mic_combo.get_active()
        audio_cfg = self.config.setdefault('audio', {})
        if idx <= 0:
            audio_cfg['device_index'] = None
            audio_cfg['device_name'] = 'Default'
            selected = None
        else:
            device = self._mic_devices[idx - 1]
            audio_cfg['device_index'] = device['index']
            audio_cfg['device_name'] = device['name']
            selected = device

        Config.save(self.config)
        if self.on_device_change:
            self.on_device_change(selected)

    def set_text(self, text):
        self.text_buffer.set_text(text or "")

    def get_text(self):
        start, end = self.text_buffer.get_bounds()
        return self.text_buffer.get_text(start, end, True)

    def auto_submit_enabled(self):
        return self.auto_submit.get_active()

    def _add_history(self, text):
        if not text:
            return
        item = self.history_store.add(text, role="user")
        self._append_message(item)

    def add_history(self, text):
        self._add_history(text)

    def _reload_history(self):
        self._clear_list(self.chat_list)
        for item in reversed(self.history_store.items()):
            self._append_message(item, prepend=False)

    def _create_history_row(self, item):
        row = Gtk.ListBoxRow()
        row.add_css_class("chat-row")
        align = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL)
        role = item.get('role', 'user')
        align.set_halign(Gtk.Align.END if role == 'user' else Gtk.Align.START)
        bubble = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        bubble.add_css_class("chat-bubble")
        bubble.add_css_class("chat-bubble-user" if role == 'user' else "chat-bubble-assistant")

        text_label = Gtk.Label(label=item.get('text', ''))
        text_label.set_xalign(0.0)
        text_label.set_wrap(True)
        text_label.set_max_width_chars(48)
        text_label.add_css_class("chat-text")
        ts_label = Gtk.Label(label=item.get('timestamp', ''))
        ts_label.set_xalign(0.0)
        ts_label.set_opacity(0.6)
        ts_label.add_css_class("muted-label")
        bubble.append(text_label)
        bubble.append(ts_label)
        align.append(bubble)
        row.set_child(align)
        row.history_text = item.get('text', '')
        return row

    def _append_message(self, item, prepend=True):
        row = self._create_history_row(item)
        self.chat_list.append(row)
        try:
            adj = self.chat_scroll.get_vadjustment()
            adj.set_value(adj.get_upper() - adj.get_page_size())
        except Exception:
            pass

    def _on_open_project(self, _button):
        dialog = Gtk.FileChooserDialog(
            title="Select Project Folder",
            action=Gtk.FileChooserAction.SELECT_FOLDER,
            transient_for=self.get_root(),
            modal=True,
        )
        dialog.add_buttons("Cancel", Gtk.ResponseType.CANCEL, "Open", Gtk.ResponseType.OK)
        dialog.set_current_folder(Gio.File.new_for_path(str(self.project_root)))

        def _on_response(dlg, response_id):
            if response_id == Gtk.ResponseType.OK:
                folder = dlg.get_file()
                if folder:
                    path = folder.get_path()
                    if path:
                        self._set_project_root(Path(path))
            dlg.destroy()

        dialog.connect("response", _on_response)
        dialog.present()

    def _on_refresh_files(self, _button):
        self._refresh_file_list()

    def _on_new_file(self, _button):
        dialog = Gtk.FileChooserDialog(
            title="Create File",
            action=Gtk.FileChooserAction.SAVE,
            transient_for=self.get_root(),
            modal=True,
        )
        dialog.add_buttons("Cancel", Gtk.ResponseType.CANCEL, "Create", Gtk.ResponseType.OK)
        dialog.set_current_folder(Gio.File.new_for_path(str(self.project_root)))

        def _on_response(dlg, response_id):
            if response_id == Gtk.ResponseType.OK:
                file = dlg.get_file()
                if file:
                    filename = file.get_path()
                    if filename:
                        Path(filename).write_text("", encoding="utf-8")
                        self._open_file(Path(filename))
                        self._refresh_file_list()
            dlg.destroy()

        dialog.connect("response", _on_response)
        dialog.present()

    def _on_tab_switched(self, _notebook, page, _page_num):
        path = self._page_paths.get(page)
        if path:
            self.file_label.set_text(str(path))
            self.current_file = str(path)

    def _get_current_path(self):
        if self.current_file:
            return self.current_file
        page = self.editor_tabs.get_nth_page(self.editor_tabs.get_current_page())
        return self._page_paths.get(page)

    def _on_file_activated(self, _listbox, row):
        if not row:
            return
        path = getattr(row, "file_path", None)
        if path:
            self._open_file(Path(path))

    def _on_save_file(self, _button):
        path = self._get_current_path()
        if not path:
            self._on_new_file(_button)
            return
        content = self._get_editor_text(path)
        try:
            Path(path).write_text(content, encoding="utf-8")
            self._set_modified(path, False)
        except Exception:
            pass

    def _on_diff_file(self, _button):
        path = self._get_current_path()
        if not path:
            return
        file_path = Path(path)
        try:
            original = file_path.read_text(encoding="utf-8").splitlines()
        except Exception:
            original = []
        current = self._get_editor_text(path).splitlines()
        diff = list(difflib.unified_diff(original, current, fromfile=str(file_path), tofile="modified"))
        self._show_diff_dialog("\n".join(diff) or "No changes.")

    def _show_diff_dialog(self, diff_text):
        dialog = Gtk.Dialog(title="Diff", transient_for=self.get_root(), modal=True)
        dialog.add_button("Close", Gtk.ResponseType.CLOSE)
        dialog.set_default_size(700, 500)

        content = dialog.get_content_area()
        textview = Gtk.TextView()
        textview.set_editable(False)
        textview.set_monospace(True)
        textview.get_buffer().set_text(diff_text)

        scroll = Gtk.ScrolledWindow()
        scroll.set_child(textview)
        content.append(scroll)

        dialog.connect("response", lambda d, _r: d.destroy())
        dialog.present()

    def _on_send_selection(self, _button):
        path = self._get_current_path()
        if not path:
            return
        buffer = self._open_files[path]['buffer']
        if buffer.get_has_selection():
            start, end = buffer.get_selection_bounds()
            text = buffer.get_text(start, end, True)
        else:
            text = self._get_editor_text(path)
        text = text.strip()
        if text and self.on_send:
            if path:
                rel = Path(path).relative_to(self.project_root)
                text = f"File: {rel}\n\n{text}"
            self.on_send(text)
            self._add_history(text)

    def _get_editor_text(self, path):
        entry = self._open_files.get(path)
        if not entry:
            return ""
        buffer = entry['buffer']
        start, end = buffer.get_bounds()
        return buffer.get_text(start, end, True)

    def _on_editor_changed(self, buffer, path):
        if not buffer.get_modified():
            buffer.set_modified(True)
        self._set_modified(path, True)

    def _set_modified(self, path, modified):
        entry = self._open_files.get(path)
        if not entry:
            return
        label = entry['label']
        name = Path(path).name
        label.set_text(f"{name}*" if modified else name)
        self._update_tree_modified(path, modified)
        entry['modified'] = modified

    def _update_tree_modified(self, path, modified):
        label = self._file_rows.get(str(path))
        if label:
            try:
                rel = Path(path).relative_to(self.project_root)
            except Exception:
                rel = Path(path).name
            label.set_text(f"{rel}*" if modified else str(rel))

    def _open_file(self, path, line=None):
        if path in self._open_files:
            self._switch_to_tab(path)
            if line is not None:
                self._scroll_to_line(path, line)
            return

        try:
            content = path.read_text(encoding="utf-8")
        except Exception:
            content = ""

        text_view = Gtk.TextView()
        text_view.add_css_class("code-editor")
        text_view.set_wrap_mode(Gtk.WrapMode.NONE)
        text_view.set_monospace(True)
        text_view.set_vexpand(True)

        buffer = text_view.get_buffer()
        buffer.set_text(content)
        buffer.set_modified(False)
        buffer.connect("changed", self._on_editor_changed, str(path))

        scroll = Gtk.ScrolledWindow()
        scroll.set_child(text_view)
        scroll.set_vexpand(True)
        scroll.set_policy(Gtk.PolicyType.AUTOMATIC, Gtk.PolicyType.AUTOMATIC)

        label_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=4)
        label = Gtk.Label(label=path.name)
        close = _icon_button("window-close-symbolic", "Close tab", "ghost-button")
        close.connect("clicked", self._on_close_tab, str(path))
        label_box.append(label)
        label_box.append(close)

        page_num = self.editor_tabs.append_page(scroll, label_box)
        self.editor_tabs.set_current_page(page_num)

        self._open_files[str(path)] = {
            'buffer': buffer,
            'view': text_view,
            'scroll': scroll,
            'label': label,
            'modified': False,
        }
        self._page_paths[scroll] = str(path)
        self.current_file = str(path)
        self.file_label.set_text(str(path))

        if line is not None:
            self._scroll_to_line(str(path), line)

    def _switch_to_tab(self, path):
        for page in range(self.editor_tabs.get_n_pages()):
            widget = self.editor_tabs.get_nth_page(page)
            if self._page_paths.get(widget) == path:
                self.editor_tabs.set_current_page(page)
                self.current_file = path
                self.file_label.set_text(path)
                return

    def _scroll_to_line(self, path, line):
        entry = self._open_files.get(path)
        if not entry:
            return
        buffer = entry['buffer']
        view = entry['view']
        line_index = max(0, int(line) - 1)
        try:
            it = buffer.get_iter_at_line(line_index)
            buffer.place_cursor(it)
            view.scroll_to_iter(it, 0.1, True, 0.0, 0.1)
        except Exception:
            pass

    def _on_close_tab(self, _button, path):
        entry = self._open_files.get(path)
        if not entry:
            return
        page_widget = entry['scroll']
        for page in range(self.editor_tabs.get_n_pages()):
            widget = self.editor_tabs.get_nth_page(page)
            if widget == page_widget:
                self.editor_tabs.remove_page(page)
                break
        self._open_files.pop(path, None)
        self._page_paths.pop(page_widget, None)
        if self.current_file == path:
            self.current_file = None
            self.file_label.set_text("No file open")

    def _on_file_search_changed(self, *_args):
        self._apply_file_filter()

    def _on_search_files(self, _button):
        query = self.file_search.get_text().strip()
        if not query:
            return
        self.search_revealer.set_reveal_child(True)
        self._clear_list(self.search_results)
        row = Gtk.ListBoxRow()
        row.set_child(Gtk.Label(label="Searching..."))
        self.search_results.append(row)

        def _worker():
            results = []
            for path in self._file_entries:
                if len(results) >= MAX_SEARCH_RESULTS:
                    break
                if path.is_dir():
                    continue
                try:
                    if path.stat().st_size > MAX_FILE_SIZE:
                        continue
                    text = path.read_text(encoding="utf-8", errors="ignore")
                except Exception:
                    continue
                for idx, line in enumerate(text.splitlines(), start=1):
                    if query.lower() in line.lower():
                        results.append((path, idx, line.strip()))
                        if len(results) >= MAX_SEARCH_RESULTS:
                            break
            GLib.idle_add(self._show_search_results, query, results)

        threading.Thread(target=_worker, daemon=True).start()

    def _show_search_results(self, query, results):
        self._clear_list(self.search_results)
        if not results:
            row = Gtk.ListBoxRow()
            row.set_child(Gtk.Label(label=f"No matches for '{query}'"))
            self.search_results.append(row)
            return False
        for path, line_no, line in results:
            label = Gtk.Label(label=f"{path.relative_to(self.project_root)}:{line_no}  {line}")
            label.set_xalign(0.0)
            label.set_wrap(True)
            row = Gtk.ListBoxRow()
            row.set_child(label)
            row.result_path = str(path)
            row.result_line = line_no
            self.search_results.append(row)
        return False

    def _on_clear_results(self, _button):
        self.search_revealer.set_reveal_child(False)
        self._clear_list(self.search_results)

    def _on_result_activated(self, _listbox, row):
        path = getattr(row, "result_path", None)
        line = getattr(row, "result_line", None)
        if path:
            self._open_file(Path(path), line=line)

    def _refresh_file_list(self):
        if self._scan_thread and self._scan_thread.is_alive():
            return
        root = self.project_root
        self._file_entries = []
        self._file_rows = {}

        def _worker():
            entries = []
            max_files = 2000
            for dirpath, dirnames, filenames in os.walk(root):
                dirnames[:] = [d for d in dirnames if d not in IGNORE_DIRS]
                for filename in filenames:
                    if len(entries) >= max_files:
                        break
                    entries.append(Path(dirpath) / filename)
                if len(entries) >= max_files:
                    break
            GLib.idle_add(self._on_files_scanned, entries)

        self._scan_thread = threading.Thread(target=_worker, daemon=True)
        self._scan_thread.start()

    def _on_files_scanned(self, entries):
        self._file_entries = entries
        self._apply_file_filter()
        return False

    def _apply_file_filter(self):
        query = self.file_search.get_text().strip().lower() if self.file_search else ''
        show_hidden = self.show_hidden.get_active() if self.show_hidden else False
        shown = []
        for path in self._file_entries:
            try:
                rel = path.relative_to(self.project_root)
            except Exception:
                continue
            if not show_hidden and any(part.startswith('.') for part in rel.parts):
                continue
            if query and query not in str(rel).lower():
                continue
            shown.append(path)
        self._populate_file_list(shown)

    def _populate_file_list(self, entries):
        self._clear_list(self.file_list)
        self._file_rows = {}
        theme = self.config.get('ui', {}).get('theme', 'dark')
        text_color = "#1b2233" if theme == "light" else "#e6e9f0"
        for path in sorted(entries):
            try:
                rel = path.relative_to(self.project_root)
            except Exception:
                rel = path
            row = Gtk.ListBoxRow()
            row.add_css_class("file-row")
            safe = GLib.markup_escape_text(str(rel))
            label = Gtk.Label()
            label.set_use_markup(True)
            label.set_markup(f'<span foreground="{text_color}">{safe}</span>')
            label.set_xalign(0.0)
            label.set_ellipsize(Pango.EllipsizeMode.MIDDLE)
            label.set_hexpand(True)
            label.add_css_class("file-label")
            row.set_child(label)
            row.file_path = str(path)
            self._file_rows[str(path)] = label
            self.file_list.append(row)
        self.file_count.set_text(f"{len(entries)} / {len(self._file_entries)} files")
        return False

    def _set_project_root(self, root):
        self.project_root = root
        self.project_path_label.set_text(str(root))
        self.config.setdefault('project', {})['root'] = str(root)
        self.config.setdefault('terminal', {})['working_directory'] = str(root)
        Config.save(self.config)
        self._refresh_file_list()

    @staticmethod
    def _clear_list(listbox):
        child = listbox.get_first_child()
        while child is not None:
            next_child = child.get_next_sibling()
            listbox.remove(child)
            child = next_child


def _icon_button(icon_name, tooltip, css_class):
    button = Gtk.Button()
    button.add_css_class("icon-button")
    button.add_css_class(css_class)
    image = Gtk.Image.new_from_icon_name(icon_name)
    button.set_child(image)
    button.set_tooltip_text(tooltip)
    return button
