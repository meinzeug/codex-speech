from codex_stt_assistant.terminal.input_injector import inject_text


class DummyTerminal:
    def __init__(self):
        self.calls = []

    def feed_child(self, data):
        self.calls.append(data)


def test_terminal_input_injection_block():
    terminal = DummyTerminal()
    inject_text(terminal, "test command")

    assert terminal.calls[0] == b'test command'
    assert terminal.calls[1] == b'\n'


def test_terminal_input_injection_line_mode():
    terminal = DummyTerminal()
    inject_text(terminal, "line1\nline2", mode='line', scheduler=False)

    assert terminal.calls == [b'line1', b'\n', b'line2', b'\n']
