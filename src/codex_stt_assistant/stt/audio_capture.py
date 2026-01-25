import threading


class AudioCapture:
    def __init__(self, rate, chunk, device_index, on_audio, on_error=None):
        self.rate = rate
        self.chunk = chunk
        self.device_index = device_index
        self.on_audio = on_audio
        self.on_error = on_error
        self._thread = None
        self._stop_event = threading.Event()
        self._stream = None
        self._pyaudio = None
        self.actual_rate = rate
        self.actual_channels = 1

    def start(self):
        if self._thread and self._thread.is_alive():
            return
        self._stop_event.clear()
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def stop(self):
        self._stop_event.set()
        if self._thread:
            self._thread.join(timeout=1.0)
        self._close_stream()

    def _run(self):
        try:
            import pyaudio
            self._pyaudio = pyaudio.PyAudio()
            self._stream = self._open_stream(self.rate)
        except Exception as exc:
            if self.on_error:
                self.on_error(str(exc))
            return

        while not self._stop_event.is_set():
            try:
                data = self._stream.read(self.chunk, exception_on_overflow=False)
            except Exception as exc:
                if self.on_error:
                    self.on_error(str(exc))
                break
            if self.on_audio:
                self.on_audio((data, self.actual_rate, self.actual_channels))

        self._close_stream()

    def _close_stream(self):
        if self._stream:
            try:
                self._stream.stop_stream()
                self._stream.close()
            except Exception:
                pass
            self._stream = None
        if self._pyaudio:
            try:
                self._pyaudio.terminate()
            except Exception:
                pass
            self._pyaudio = None

    def _open_stream(self, rate):
        import pyaudio

        attempts = []
        if self.device_index is not None:
            attempts.append(self.device_index)
        attempts.append(None)

        last_exc = None
        for device_index in attempts:
            try:
                return self._open_stream_with(rate, 1, device_index)
            except Exception as exc:
                last_exc = exc
                fallback_rate = self._default_rate(rate, device_index)
                try:
                    return self._open_stream_with(fallback_rate, 1, device_index)
                except Exception as exc2:
                    last_exc = exc2
                    try:
                        return self._open_stream_with(fallback_rate, 2, device_index)
                    except Exception as exc3:
                        last_exc = exc3
                        if device_index is not None and self.on_error:
                            self.on_error(
                                f"Audio device {device_index} unavailable; falling back to default."
                            )

        if last_exc:
            raise last_exc
        raise RuntimeError("Failed to open audio input stream.")

    def _open_stream_with(self, rate, channels, device_index):
        import pyaudio
        stream = self._pyaudio.open(
            format=pyaudio.paInt16,
            channels=channels,
            rate=rate,
            input=True,
            frames_per_buffer=self.chunk,
            input_device_index=device_index,
        )
        self.actual_rate = rate
        self.actual_channels = channels
        self.device_index = device_index
        return stream

    def _default_rate(self, fallback, device_index):
        try:
            if device_index is not None:
                info = self._pyaudio.get_device_info_by_index(device_index)
            else:
                info = self._pyaudio.get_default_input_device_info()
            return int(info.get('defaultSampleRate', fallback))
        except Exception:
            return fallback
