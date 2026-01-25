from abc import ABC, abstractmethod


class STTEngine(ABC):
    def __init__(self, config, on_result=None, on_partial=None, on_status=None, on_level=None):
        self.config = config
        self.on_result = on_result
        self.on_partial = on_partial
        self.on_status = on_status
        self.on_level = on_level

    @abstractmethod
    def start(self):
        raise NotImplementedError

    @abstractmethod
    def stop(self):
        raise NotImplementedError

    def emit_result(self, text):
        if self.on_result:
            self.on_result(text)

    def emit_partial(self, text):
        if self.on_partial:
            self.on_partial(text)

    def emit_status(self, status):
        if self.on_status:
            self.on_status(status)

    def emit_level(self, level):
        if self.on_level:
            self.on_level(level)
