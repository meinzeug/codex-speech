from pathlib import Path
import os
import re
import shutil

try:
    import gi
    gi.require_version('Pango', '1.0')
    gi.require_version('Vte', '3.91')
    from gi.repository import GLib, Pango, Vte
    _VTE_IMPORT_ERROR = None
except Exception as exc:
    GLib = None
    Pango = None
    Vte = None
    _VTE_IMPORT_ERROR = str(exc)

from .input_injector import inject_text


class PTYManager:
    def __init__(self, config, on_exit=None):
        if Vte is None:
            detail = f" ({_VTE_IMPORT_ERROR})" if _VTE_IMPORT_ERROR else ""
            raise RuntimeError(
                'Vte is not available. Install libvte-2.91-gtk4-0 and gir1.2-vte-3.91' + detail
            )
        self.config = config
        self.on_exit = on_exit
        self.terminal = Vte.Terminal()
        self._configure_terminal()
        self.terminal.connect('child-exited', self._on_child_exited)

    def spawn(self):
        term_cfg = self.config.get('terminal', {}) if self.config else {}
        working_dir = Path(term_cfg.get('working_directory', '~')).expanduser()
        codex_path = term_cfg.get('codex_path', 'codex')
        args = term_cfg.get('codex_args', [])

        resolved_codex = _resolve_codex_path(codex_path)
        if not resolved_codex:
            raise RuntimeError('Codex binary not found. Set the Codex path in Settings.')

        argv = [resolved_codex] + args
        envv = _build_env_with_path(resolved_codex)
        self.terminal.spawn_async(
            Vte.PtyFlags.DEFAULT,
            str(working_dir),
            argv,
            envv,
            GLib.SpawnFlags.DEFAULT,
            None,
            None,
            -1,
            None,
            None,
            None,
        )

    def send_text(self, text):
        mode = self.config.get('terminal', {}).get('send_mode', 'block') if self.config else 'block'
        inject_text(self.terminal, text, mode=mode)

    def send_raw(self, text):
        """Send raw text to the terminal without any processing or newlines."""
        if self.terminal:
            self.terminal.feed_child(text.encode('utf-8'))

    def reset(self):
        self.terminal.reset(True, True)

    def _configure_terminal(self):
        term_cfg = self.config.get('terminal', {}) if self.config else {}
        font_family = term_cfg.get('font_family', 'Monospace')
        font_size = term_cfg.get('font_size', 11)
        scrollback = term_cfg.get('scrollback_lines', 10000)

        self.terminal.set_font(Pango.FontDescription(f"{font_family} {font_size}"))
        self.terminal.set_scrollback_lines(scrollback)
        self.terminal.set_scroll_on_output(True)
        self.terminal.set_cursor_blink_mode(Vte.CursorBlinkMode.ON)

    def _on_child_exited(self, terminal, status):
        if self.on_exit:
            self.on_exit(status)


def _resolve_codex_path(codex_path):
    if os.environ.get("CODEX_STT_NO_TERMINAL") == "1":
        return None
    if codex_path:
        candidate = Path(codex_path).expanduser()
        if candidate.is_absolute() and candidate.exists():
            return str(candidate)

    if codex_path:
        found = shutil.which(str(codex_path))
        if found:
            return found

    # Try common nvm locations
    nvm_candidates = list(Path.home().glob('.nvm/versions/node/*/bin/codex'))
    if nvm_candidates:
        return str(_pick_latest_nvm(nvm_candidates))

    return None


def _pick_latest_nvm(paths):
    def key(path):
        name = path.parents[1].name  # e.g. v22.20.0
        match = re.findall(r'\d+', name)
        if not match:
            return (0,)
        return tuple(int(x) for x in match)

    return sorted(paths, key=key, reverse=True)[0]


def _build_env_with_path(codex_path):
    env = os.environ.copy()
    codex_dir = str(Path(codex_path).parent)
    current_path = env.get('PATH', '')
    if codex_dir and codex_dir not in current_path.split(':'):
        env['PATH'] = f"{codex_dir}:{current_path}" if current_path else codex_dir
    env.setdefault('TERM', 'xterm-256color')
    return [f"{k}={v}" for k, v in env.items()]
