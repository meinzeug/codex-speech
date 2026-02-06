from codex_stt_assistant.stt import create_engine
from codex_stt_assistant.stt.vosk_engine import VoskEngine
from codex_stt_assistant.stt.whisper_engine import WhisperEngine


def test_create_engine_default_vosk():
    engine = create_engine({'stt': {'engine': 'vosk'}, 'audio': {}})
    assert isinstance(engine, VoskEngine)


def test_create_engine_whisper():
    engine = create_engine({'stt': {'engine': 'whisper'}, 'audio': {}})
    assert isinstance(engine, WhisperEngine)
