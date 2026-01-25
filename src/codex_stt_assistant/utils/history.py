import json
from datetime import datetime
from pathlib import Path


class HistoryStore:
    def __init__(self, max_items=50, path=None):
        self.max_items = max_items
        self.path = Path(path).expanduser() if path else self._default_path()
        self._items = []
        self.load()

    def load(self):
        if not self.path.exists():
            self._items = []
            return self._items
        with self.path.open('r', encoding='utf-8') as handle:
            self._items = json.load(handle)
        return self._items

    def save(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self.path.open('w', encoding='utf-8') as handle:
            json.dump(self._items[: self.max_items], handle, indent=2)

    def add(self, text, role="user"):
        item = {
            "text": text,
            "timestamp": datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            "role": role,
        }
        self._items.insert(0, item)
        self._items = self._items[: self.max_items]
        self.save()
        return item

    def items(self):
        return list(self._items)

    def clear(self):
        self._items = []
        self.save()

    def _default_path(self):
        return Path.home() / '.local' / 'share' / 'codex-stt-assistant' / 'history.json'
