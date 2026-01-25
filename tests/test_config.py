import tempfile
from pathlib import Path

from codex_stt_assistant.config.config_manager import Config
from codex_stt_assistant.config.defaults import DEFAULT_CONFIG


def test_config_load_save():
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        Config.CONFIG_DIR = tmp_path
        Config.CONFIG_FILE = tmp_path / 'config.json'

        Config.save({'ui': {'theme': 'light'}})
        loaded = Config.load()
        assert loaded['ui']['theme'] == 'light'
        assert loaded['ui']['history_size'] == DEFAULT_CONFIG['ui']['history_size']
