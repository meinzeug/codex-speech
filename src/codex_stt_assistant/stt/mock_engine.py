import threading

from .base_engine import STTEngine


class MockEngine(STTEngine):
    def __init__(self, config, on_result=None, on_partial=None, on_status=None, on_level=None):
        super().__init__(
            config,
            on_result=on_result,
            on_partial=on_partial,
            on_status=on_status,
            on_level=on_level,
        )
        self._timer = None

    def start(self):
        self.emit_status('recording')
        self._timer = threading.Timer(0.6, self._emit)
        self._timer.daemon = True
        self._timer.start()

    def stop(self):
        if self._timer:
            self._timer.cancel()
        self.emit_status('idle')

    def _emit(self):
        self.emit_level(0.4)
        self.emit_result('mock transcription')
        self.emit_status('idle')
