from .base_engine import STTEngine
import os

from .vosk_engine import VoskEngine
from .whisper_engine import WhisperEngine
from .mock_engine import MockEngine
from .audio_devices import list_input_devices


def create_engine(config, on_result=None, on_partial=None, on_status=None, on_level=None):
    engine_name = (config.get('stt', {}).get('engine', 'vosk') if config else 'vosk').lower()
    if os.environ.get("CODEX_STT_TEST_MODE") == "1":
        return MockEngine(
            config,
            on_result=on_result,
            on_partial=on_partial,
            on_status=on_status,
            on_level=on_level,
        )
    if engine_name == 'whisper':
        return WhisperEngine(
            config,
            on_result=on_result,
            on_partial=on_partial,
            on_status=on_status,
            on_level=on_level,
        )
    return VoskEngine(
        config,
        on_result=on_result,
        on_partial=on_partial,
        on_status=on_status,
        on_level=on_level,
    )


__all__ = [
    "STTEngine",
    "VoskEngine",
    "WhisperEngine",
    "MockEngine",
    "create_engine",
    "list_input_devices",
]
