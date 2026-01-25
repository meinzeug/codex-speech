from gi.repository import Gtk

from ..stt import list_input_devices


class SettingsDialog(Gtk.Dialog):
    def __init__(self, parent, config, on_apply):
        super().__init__(title="Settings", transient_for=parent, modal=True)
        self.config = config
        self.on_apply = on_apply
        self.add_css_class("settings-dialog")

        self.add_button("Cancel", Gtk.ResponseType.CANCEL)
        self.add_button("Apply", Gtk.ResponseType.APPLY)
        self.add_button("OK", Gtk.ResponseType.OK)
        self.connect("response", self._on_response)

        content = self.get_content_area()
        notebook = Gtk.Notebook()
        notebook.add_css_class("settings-tabs")
        notebook.append_page(self._build_general_tab(), Gtk.Label(label="General"))
        notebook.append_page(self._build_stt_tab(), Gtk.Label(label="STT"))
        notebook.append_page(self._build_terminal_tab(), Gtk.Label(label="Terminal"))
        notebook.append_page(self._build_audio_tab(), Gtk.Label(label="Audio"))
        content.append(notebook)

        self.present()

    def _build_general_tab(self):
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8, margin_top=12,
                      margin_bottom=12, margin_start=12, margin_end=12)

        self.theme_combo = Gtk.ComboBoxText()
        for name in ["dark", "light", "auto"]:
            self.theme_combo.append_text(name)
        self.theme_combo.set_active(0)

        theme = self.config.get('ui', {}).get('theme', 'dark')
        if theme in ("dark", "light", "auto"):
            self.theme_combo.set_active(["dark", "light", "auto"].index(theme))

        self.auto_submit_toggle = Gtk.CheckButton(label="Auto-submit")
        self.auto_submit_toggle.set_active(self.config.get('ui', {}).get('auto_submit', True))

        self.history_size = Gtk.SpinButton.new_with_range(1, 200, 1)
        self.history_size.set_value(self.config.get('ui', {}).get('history_size', 50))

        box.append(_row("Theme", self.theme_combo))
        box.append(self.auto_submit_toggle)
        box.append(_row("History size", self.history_size))
        return box

    def _build_stt_tab(self):
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8, margin_top=12,
                      margin_bottom=12, margin_start=12, margin_end=12)

        self.engine_combo = Gtk.ComboBoxText()
        for name in ["vosk", "whisper"]:
            self.engine_combo.append_text(name)
        engine = self.config.get('stt', {}).get('engine', 'vosk')
        self.engine_combo.set_active(0 if engine == 'vosk' else 1)

        self.model_path_entry = Gtk.Entry()
        self.model_path_entry.set_text(self.config.get('stt', {}).get('model_path', ''))

        self.vad_toggle = Gtk.CheckButton(label="Enable VAD")
        self.vad_toggle.set_active(self.config.get('stt', {}).get('vad_enabled', True))

        self.whisper_model = Gtk.Entry()
        self.whisper_model.set_text(self.config.get('stt', {}).get('whisper_model', 'base'))

        self.whisper_device = Gtk.Entry()
        self.whisper_device.set_text(self.config.get('stt', {}).get('whisper_device', 'cpu'))

        self.whisper_compute = Gtk.ComboBoxText()
        for name in ["int8", "int8_float16", "float16", "float32"]:
            self.whisper_compute.append_text(name)
        compute = self.config.get('stt', {}).get('whisper_compute_type', 'int8')
        if compute in ["int8", "int8_float16", "float16", "float32"]:
            self.whisper_compute.set_active(["int8", "int8_float16", "float16", "float32"].index(compute))
        else:
            self.whisper_compute.set_active(0)

        box.append(_row("Engine", self.engine_combo))
        box.append(_row("Model path", self.model_path_entry))
        box.append(self.vad_toggle)
        box.append(_row("Whisper model", self.whisper_model))
        box.append(_row("Whisper device", self.whisper_device))
        box.append(_row("Whisper compute", self.whisper_compute))
        return box

    def _build_terminal_tab(self):
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8, margin_top=12,
                      margin_bottom=12, margin_start=12, margin_end=12)

        self.font_entry = Gtk.Entry()
        self.font_entry.set_text(self.config.get('terminal', {}).get('font_family', 'Monospace'))

        self.font_size = Gtk.SpinButton.new_with_range(8, 32, 1)
        self.font_size.set_value(self.config.get('terminal', {}).get('font_size', 11))

        self.codex_path = Gtk.Entry()
        self.codex_path.set_text(self.config.get('terminal', {}).get('codex_path', '/usr/bin/codex'))

        self.working_dir = Gtk.Entry()
        self.working_dir.set_text(self.config.get('terminal', {}).get('working_directory', '~'))

        self.auto_restart = Gtk.CheckButton(label="Auto-restart on crash")
        self.auto_restart.set_active(self.config.get('terminal', {}).get('auto_restart_on_crash', True))

        box.append(_row("Font", self.font_entry))
        box.append(_row("Font size", self.font_size))
        box.append(_row("Codex path", self.codex_path))
        box.append(_row("Working dir", self.working_dir))
        box.append(self.auto_restart)
        return box

    def _build_audio_tab(self):
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8, margin_top=12,
                      margin_bottom=12, margin_start=12, margin_end=12)

        self.device_combo = Gtk.ComboBoxText()
        self._devices = []
        self._refresh_devices()

        self.sample_rate = Gtk.SpinButton.new_with_range(8000, 48000, 1000)
        self.sample_rate.set_value(self.config.get('audio', {}).get('sample_rate', 16000))

        self.chunk_size = Gtk.SpinButton.new_with_range(256, 8192, 256)
        self.chunk_size.set_value(self.config.get('audio', {}).get('chunk_size', 4096))

        refresh_button = Gtk.Button(label="Refresh devices")
        refresh_button.connect("clicked", self._on_refresh_devices)

        box.append(_row("Input device", self.device_combo))
        box.append(refresh_button)
        box.append(_row("Sample rate", self.sample_rate))
        box.append(_row("Chunk size", self.chunk_size))
        return box

    def _on_response(self, _dialog, response_id):
        if response_id in (Gtk.ResponseType.APPLY, Gtk.ResponseType.OK):
            self._apply_changes()
            if self.on_apply:
                self.on_apply(self.config)
        if response_id in (Gtk.ResponseType.OK, Gtk.ResponseType.CANCEL):
            self.destroy()

    def _apply_changes(self):
        self.config['ui']['theme'] = self.theme_combo.get_active_text()
        self.config['ui']['auto_submit'] = self.auto_submit_toggle.get_active()
        self.config['ui']['history_size'] = int(self.history_size.get_value())

        self.config['stt']['engine'] = self.engine_combo.get_active_text()
        self.config['stt']['model_path'] = self.model_path_entry.get_text()
        self.config['stt']['vad_enabled'] = self.vad_toggle.get_active()
        self.config['stt']['whisper_model'] = self.whisper_model.get_text() or 'base'
        self.config['stt']['whisper_device'] = self.whisper_device.get_text() or 'cpu'
        self.config['stt']['whisper_compute_type'] = self.whisper_compute.get_active_text() or 'int8'

        self.config['terminal']['font_family'] = self.font_entry.get_text()
        self.config['terminal']['font_size'] = int(self.font_size.get_value())
        self.config['terminal']['codex_path'] = self.codex_path.get_text()
        self.config['terminal']['working_directory'] = self.working_dir.get_text()
        self.config['terminal']['auto_restart_on_crash'] = self.auto_restart.get_active()

        self.config['audio']['sample_rate'] = int(self.sample_rate.get_value())
        self.config['audio']['chunk_size'] = int(self.chunk_size.get_value())
        self._apply_device_selection()

    def _refresh_devices(self):
        self.device_combo.remove_all()
        self._devices = list_input_devices()
        self.device_combo.append_text("Default")
        for dev in self._devices:
            label = f"{dev['name']} ({dev['rate']} Hz)"
            self.device_combo.append_text(label)

        selected = 0
        config_idx = self.config.get('audio', {}).get('device_index')
        if config_idx is not None:
            for idx, dev in enumerate(self._devices):
                if dev['index'] == config_idx:
                    selected = idx + 1
                    break
        self.device_combo.set_active(selected)

    def _on_refresh_devices(self, _button):
        self._refresh_devices()

    def _apply_device_selection(self):
        selected = self.device_combo.get_active()
        if selected <= 0:
            self.config['audio']['device_index'] = None
            self.config['audio']['device_name'] = 'Default'
            return
        device = self._devices[selected - 1]
        self.config['audio']['device_index'] = device['index']
        self.config['audio']['device_name'] = device['name']


def _row(label_text, widget):
    row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
    label = Gtk.Label(label=label_text)
    label.set_xalign(0.0)
    label.set_size_request(120, -1)
    row.append(label)
    row.append(widget)
    return row
