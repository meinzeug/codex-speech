from .input_injector import inject_text

try:
    from .pty_manager import PTYManager
except Exception:  # pragma: no cover - optional dependency
    PTYManager = None

__all__ = ["PTYManager", "inject_text"]
