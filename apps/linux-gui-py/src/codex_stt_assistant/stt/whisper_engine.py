import audioop
import threading
import time

from .base_engine import STTEngine
from .audio_capture import AudioCapture


class WhisperEngine(STTEngine):
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

        self.model_name = stt_config.get('whisper_model', 'base')
        self.device = stt_config.get('whisper_device', 'cpu')
        self.compute_type = stt_config.get('whisper_compute_type', 'int8')
        self.language = stt_config.get('language', None)
        self.vad_enabled = stt_config.get('vad_enabled', True)
        self.target_rate = int(stt_config.get('target_rate', 16000))

        self.sample_rate = int(audio_config.get('sample_rate', 16000))
        self.chunk_size = int(audio_config.get('chunk_size', 4096))
        self.device_index = audio_config.get('device_index')

        self._audio_frames = []
        self._audio_lock = threading.Lock()
        self._stop_event = threading.Event()
        self._transcribe_thread = None
        self._model = None
        self._backend = None
        self._rate_state = None
        self._last_level_ts = 0.0

        self._capture = AudioCapture(
            rate=self.sample_rate,
            chunk=self.chunk_size,
            device_index=self.device_index,
            on_audio=self._on_audio,
            on_error=self._handle_error,
        )

    def start(self):
        if self._transcribe_thread and self._transcribe_thread.is_alive():
            return
        self._stop_event.clear()
        self._rate_state = None
        with self._audio_lock:
            self._audio_frames = []
        self.emit_status('recording')
        self._capture.start()

    def stop(self):
        self._stop_event.set()
        self._capture.stop()
        frames = self._drain_frames()
        if not frames:
            self.emit_status('idle')
            return
        self.emit_status('transcribing')
        self._transcribe_thread = threading.Thread(
            target=self._transcribe, args=(frames,), daemon=True
        )
        self._transcribe_thread.start()

    def _on_audio(self, item):
        if self._stop_event.is_set():
            return
        self._emit_level(item)
        with self._audio_lock:
            self._audio_frames.append(item)

    def _drain_frames(self):
        with self._audio_lock:
            frames = self._audio_frames
            self._audio_frames = []
        return frames

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

    def _transcribe(self, frames):
        pcm, rate = self._normalize_audio(frames)
        if not pcm:
            self.emit_status('idle')
            return

        if not self._ensure_model():
            self.emit_status('idle')
            return

        try:
            import numpy as np
        except Exception as exc:
            self.emit_status(f'error: numpy missing ({exc})')
            self.emit_status('idle')
            return

        audio = np.frombuffer(pcm, np.int16).astype(np.float32) / 32768.0
        text = ""

        try:
            if self._backend == 'faster':
                kwargs = {
                    "language": self.language or None,
                    "vad_filter": bool(self.vad_enabled),
                }
                segments, _info = self._model.transcribe(audio, **kwargs)
                parts = []
                for seg in segments:
                    segment_text = getattr(seg, "text", "").strip()
                    if segment_text:
                        parts.append(segment_text)
                text = " ".join(parts).strip()
            else:
                result = self._model.transcribe(
                    audio,
                    language=self.language or None,
                    fp16=False,
                )
                text = result.get("text", "").strip()
        except Exception as exc:
            self.emit_status(f'error: whisper failed ({exc})')
            self.emit_status('idle')
            return

        if text:
            self.emit_result(text)
        self.emit_status('idle')

    def _ensure_model(self):
        if self._model:
            return True
        self.emit_status('loading')
        try:
            from faster_whisper import WhisperModel
            self._backend = 'faster'
            self._model = WhisperModel(
                self.model_name,
                device=self.device,
                compute_type=self.compute_type,
            )
            return True
        except Exception:
            pass

        try:
            import whisper
            self._backend = 'openai'
            self._model = whisper.load_model(self.model_name, device=self.device)
            return True
        except Exception as exc:
            self.emit_status(
                f'error: whisper not available ({exc}). Install faster-whisper or openai-whisper.'
            )
            return False

    def _normalize_audio(self, frames):
        state = None
        out = bytearray()
        rate = self.sample_rate
        for item in frames:
            if isinstance(item, tuple):
                data = item[0]
                rate = item[1]
                channels = item[2] if len(item) > 2 else 1
            else:
                data = item
                channels = 1
            if channels and channels > 1:
                try:
                    data = audioop.tomono(data, 2, 0.5, 0.5)
                except Exception:
                    pass
            if rate != self.target_rate:
                try:
                    data, state = audioop.ratecv(
                        data, 2, 1, rate, self.target_rate, state
                    )
                except Exception:
                    pass
            out.extend(data)
        return bytes(out), self.target_rate
