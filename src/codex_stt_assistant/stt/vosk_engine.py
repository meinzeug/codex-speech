import json
import queue
import threading
import audioop
import time
from pathlib import Path

from .base_engine import STTEngine
from .audio_capture import AudioCapture


class VoskEngine(STTEngine):
    def __init__(self, config, on_result=None, on_partial=None, on_status=None, on_level=None):
        super().__init__(
            config,
            on_result=on_result,
            on_partial=on_partial,
            on_status=on_status,
            on_level=on_level,
        )
        stt_config = config.get('stt', {}) if config else {}
        audio_config = config.get('audio', {}) if config else {}

        self.model_path = Path(stt_config.get('model_path', '')).expanduser()
        self.sample_rate = int(audio_config.get('sample_rate', 16000))
        self.target_rate = int(stt_config.get('target_rate', 16000))
        self.chunk_size = int(audio_config.get('chunk_size', 4096))
        self.device_index = audio_config.get('device_index')

        self._audio_queue = queue.Queue()
        self._stop_event = threading.Event()
        self._worker_thread = None
        self._loading_thread = None
        self._recognizer = None
        self._rate_state = None
        self._last_level_ts = 0.0
        self._capture = AudioCapture(
            rate=self.sample_rate,
            chunk=self.chunk_size,
            device_index=self.device_index,
            on_audio=self._audio_queue.put,
            on_error=self._handle_error,
        )

    def start(self):
        if self._worker_thread and self._worker_thread.is_alive():
            return
        if self._loading_thread and self._loading_thread.is_alive():
            return
        self._stop_event.clear()
        self._rate_state = None
        self.emit_status('loading')
        self._loading_thread = threading.Thread(target=self._start_pipeline, daemon=True)
        self._loading_thread.start()

    def stop(self):
        self.emit_status('stopping')
        self._stop_event.set()
        self._capture.stop()
        if self._worker_thread:
            self._worker_thread.join(timeout=1.0)
        if self._loading_thread:
            self._loading_thread.join(timeout=1.0)
        self._rate_state = None
        self.emit_status('idle')

    def _load_model(self):
        try:
            import vosk
        except Exception as exc:
            self.emit_status(f'error: missing vosk ({exc})')
            return False

        if not self.model_path.exists():
            self.emit_status(f'error: model not found at {self.model_path}')
            return False

        vosk.SetLogLevel(-1)
        try:
            model = vosk.Model(str(self.model_path))
            self._recognizer = vosk.KaldiRecognizer(model, self.target_rate)
        except Exception as exc:
            self.emit_status(f'error: failed to load model ({exc})')
            return False
        return True

    def _start_pipeline(self):
        if not self._load_model():
            self.emit_status('idle')
            return
        if self._stop_event.is_set():
            self.emit_status('idle')
            return
        self.emit_status('recording')
        self._capture.start()
        self._worker_thread = threading.Thread(target=self._process_loop, daemon=True)
        self._worker_thread.start()

    def _process_loop(self):
        while not self._stop_event.is_set():
            try:
                data = self._audio_queue.get(timeout=0.1)
            except queue.Empty:
                continue

            if not self._recognizer:
                continue

            self._emit_level(data)
            audio_data, rate = self._normalize_audio(data)
            if self._recognizer.AcceptWaveform(audio_data):
                result = json.loads(self._recognizer.Result())
                text = result.get('text', '').strip()
                if text:
                    self.emit_result(text)
            else:
                partial = json.loads(self._recognizer.PartialResult())
                partial_text = partial.get('partial', '').strip()
                if partial_text:
                    self.emit_partial(partial_text)

    def _handle_error(self, message):
        self.emit_status(f'error: {message}')

    def _emit_level(self, item):
        try:
            now = time.time()
            if now - self._last_level_ts < 0.1:
                return
            raw = item[0] if isinstance(item, tuple) else item
            if not raw:
                return
            rms = audioop.rms(raw, 2)
            level = min(1.0, max(0.0, rms / 32768.0))
            self.emit_level(level)
            self._last_level_ts = now
        except Exception:
            pass

    def _normalize_audio(self, item):
        channels = 1
        if isinstance(item, tuple) and len(item) >= 2:
            data = item[0]
            rate = item[1]
            if len(item) >= 3:
                channels = item[2]
        else:
            data, rate = item, self.sample_rate
        if channels and channels > 1:
            try:
                data = audioop.tomono(data, 2, 0.5, 0.5)
            except Exception as exc:
                self.emit_status(f'error: downmix failed ({exc})')
        if rate != self.target_rate:
            try:
                data, self._rate_state = audioop.ratecv(
                    data, 2, 1, rate, self.target_rate, self._rate_state
                )
            except Exception as exc:
                self.emit_status(f'error: resample failed ({exc})')
        return data, self.target_rate
