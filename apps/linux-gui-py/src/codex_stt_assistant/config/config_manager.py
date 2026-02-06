import json
from pathlib import Path
from copy import deepcopy

from .defaults import DEFAULT_CONFIG


class Config:
    CONFIG_DIR = Path.home() / '.config' / 'codex-stt-assistant'
    CONFIG_FILE = CONFIG_DIR / 'config.json'

    @classmethod
    def load(cls):
        if not cls.CONFIG_FILE.exists():
            cls.CONFIG_DIR.mkdir(parents=True, exist_ok=True)
            data = deepcopy(DEFAULT_CONFIG)
            cls.save(data)
            return data

        with cls.CONFIG_FILE.open('r', encoding='utf-8') as handle:
            data = json.load(handle)

        merged = deepcopy(DEFAULT_CONFIG)
        _deep_update(merged, data)
        _migrate_codex_path(merged)
        _ensure_project_config(merged)
        audio_changed = _ensure_audio_device_config(merged)
        if audio_changed:
            cls.save(merged)
        return merged

    @classmethod
    def save(cls, config):
        cls.CONFIG_DIR.mkdir(parents=True, exist_ok=True)
        with cls.CONFIG_FILE.open('w', encoding='utf-8') as handle:
            json.dump(config, handle, indent=2)

    @staticmethod
    def get(config, key_path, default=None):
        keys = _normalize_key_path(key_path)
        current = config
        for key in keys:
            if not isinstance(current, dict) or key not in current:
                return default
            current = current[key]
        return current

    @staticmethod
    def set(config, key_path, value):
        keys = _normalize_key_path(key_path)
        current = config
        for key in keys[:-1]:
            current = current.setdefault(key, {})
        current[keys[-1]] = value
        return config


def _normalize_key_path(key_path):
    if isinstance(key_path, (list, tuple)):
        return list(key_path)
    if isinstance(key_path, str):
        return [part for part in key_path.split('.') if part]
    raise TypeError('key_path must be a list, tuple, or dot-separated string')


def _deep_update(target, source):
    for key, value in source.items():
        if isinstance(value, dict) and isinstance(target.get(key), dict):
            _deep_update(target[key], value)
        else:
            target[key] = value


def _migrate_codex_path(config):
    term_cfg = config.get('terminal', {})
    codex_path = term_cfg.get('codex_path')
    if codex_path == '/usr/bin/codex':
        term_cfg['codex_path'] = 'codex'


def _ensure_project_config(config):
    config.setdefault('project', {}).setdefault('root', '~')


def _ensure_audio_device_config(config):
    audio_cfg = config.setdefault('audio', {})
    device_index = audio_cfg.get('device_index')
    device_name = audio_cfg.get('device_name')

    if device_index is None and (not device_name or device_name == 'Default'):
        return False

    try:
        from ..stt import list_input_devices
        devices = list_input_devices()
    except Exception:
        return False

    if not devices:
        return False

    if device_index is not None:
        for dev in devices:
            if dev.get('index') == device_index:
                return False

    if device_name:
        for dev in devices:
            if dev.get('name') == device_name:
                audio_cfg['device_index'] = dev.get('index')
                return True

    audio_cfg['device_index'] = None
    audio_cfg['device_name'] = 'Default'
    return True
