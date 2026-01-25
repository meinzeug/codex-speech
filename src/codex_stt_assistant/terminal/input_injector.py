try:
    from gi.repository import GLib
except Exception:
    GLib = None

_UNSET = object()


def inject_text(terminal, text, mode='block', line_delay_ms=100, scheduler=_UNSET):
    cleaned = (text or '').strip()
    if not cleaned:
        return

    if scheduler is _UNSET and GLib:
        scheduler = GLib.timeout_add

    if mode == 'line':
        lines = cleaned.splitlines()
        if scheduler:
            _schedule_lines(terminal, lines, scheduler, line_delay_ms)
        else:
            for line in lines:
                _send_line(terminal, line)
    else:
        _send_line(terminal, cleaned)


def _schedule_lines(terminal, lines, scheduler, delay_ms):
    state = {"index": 0}

    def _send_next():
        idx = state["index"]
        if idx >= len(lines):
            return False
        _send_line(terminal, lines[idx])
        state["index"] += 1
        return True

    scheduler(delay_ms, _send_next)


def _send_line(terminal, line):
    terminal.feed_child(line.encode('utf-8'))
    terminal.feed_child(b'\n')
