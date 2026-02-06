from __future__ import annotations

import asyncio
import fcntl
import json
import logging
import os
import pty
import re
import shlex
import shutil
import subprocess
import struct
import termios
import tempfile
import threading
import time
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, UploadFile, File, Form, HTTPException
from pydantic import BaseModel

app = FastAPI()
logger = logging.getLogger("codex-backend")

CONFIG_PATH = Path(
    os.environ.get("CODEX_CONFIG", "~/.config/codex-stt-assistant/config.json")
).expanduser()

CURSOR_POS_QUERY = b"\x1b[6n"

STT_MODEL_NAME = os.environ.get("STT_MODEL", "small")
STT_DEVICE = os.environ.get("STT_DEVICE", "cpu")
STT_COMPUTE_TYPE = os.environ.get("STT_COMPUTE_TYPE", "int8")

_stt_model = None
_stt_lock = threading.Lock()


class DirCreateRequest(BaseModel):
    path: str


class DirRenameRequest(BaseModel):
    src: str
    dst: str


class DirDeleteRequest(BaseModel):
    path: str
    recursive: bool = False


class HeadlessPTY:
    def __init__(self, command: list[str], cwd: Path, env: dict[str, str]):
        self.command = command
        self.cwd = cwd
        self.env = env
        self.master_fd: Optional[int] = None
        self.slave_fd: Optional[int] = None
        self.process: Optional[subprocess.Popen] = None
        self.running = False

    def start(self):
        self.master_fd, self.slave_fd = pty.openpty()
        self._set_winsize(self.master_fd, rows=24, cols=80)
        self.process = subprocess.Popen(
            self.command,
            stdin=self.slave_fd,
            stdout=self.slave_fd,
            stderr=self.slave_fd,
            cwd=str(self.cwd),
            env=self.env,
            start_new_session=True,
            close_fds=True,
        )
        self.running = True
        os.close(self.slave_fd)

    @staticmethod
    def _set_winsize(fd: int, rows: int, cols: int):
        try:
            fcntl.ioctl(fd, termios.TIOCSWINSZ, struct.pack("HHHH", rows, cols, 0, 0))
        except OSError:
            pass

    def write(self, data: bytes):
        if self.master_fd:
            os.write(self.master_fd, data)

    def read(self, size: int = 1024) -> bytes:
        if self.master_fd:
            try:
                return os.read(self.master_fd, size)
            except OSError:
                return b""
        return b""

    def stop(self):
        self.running = False
        if self.process:
            self.process.terminate()
            self.process.wait()
        if self.master_fd:
            os.close(self.master_fd)


@app.get("/health")
def health():
    return {"status": "ok"}


def expand_dir_path(value: str) -> Path:
    if not value:
        return Path.home()
    raw = Path(value).expanduser()
    if raw.is_absolute():
        return raw
    return (Path.home() / raw).resolve()


def list_directory_suggestions(query: Optional[str], limit: int = 200) -> dict:
    raw = (query or "").strip()
    if not raw:
        base = Path.home()
        prefix = ""
    else:
        expanded = expand_dir_path(raw)
        if raw.endswith(os.sep) or (expanded.exists() and expanded.is_dir()):
            base = expanded
            prefix = ""
        else:
            base = expanded.parent
            prefix = expanded.name
    if not base.exists():
        raise HTTPException(status_code=404, detail=f"Directory not found: {base}")
    if not base.is_dir():
        raise HTTPException(status_code=400, detail=f"Not a directory: {base}")

    dirs = []
    try:
        for child in base.iterdir():
            if child.is_dir() and child.name.startswith(prefix):
                dirs.append(str(child))
    except PermissionError as exc:
        raise HTTPException(status_code=403, detail=str(exc)) from exc

    dirs.sort()
    if base.parent != base:
        parent = str(base.parent)
        if parent not in dirs:
            dirs.insert(0, parent)
    return {"base": str(base), "dirs": dirs[:limit]}


@app.get("/dirs")
def dirs(path: Optional[str] = None, limit: int = 200):
    return list_directory_suggestions(path, limit=limit)


@app.post("/dirs/create")
def dirs_create(payload: DirCreateRequest):
    target = expand_dir_path(payload.path)
    try:
        target.mkdir(parents=True, exist_ok=False)
    except FileExistsError as exc:
        raise HTTPException(status_code=409, detail="Directory already exists") from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    return {"status": "ok", "path": str(target)}


@app.post("/dirs/rename")
def dirs_rename(payload: DirRenameRequest):
    src = expand_dir_path(payload.src)
    dst = expand_dir_path(payload.dst)
    if not src.exists():
        raise HTTPException(status_code=404, detail="Source not found")
    if dst.exists():
        raise HTTPException(status_code=409, detail="Destination already exists")
    try:
        src.rename(dst)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    return {"status": "ok", "path": str(dst)}


@app.post("/dirs/delete")
def dirs_delete(payload: DirDeleteRequest):
    target = expand_dir_path(payload.path)
    if not target.exists():
        raise HTTPException(status_code=404, detail="Directory not found")
    try:
        if target.is_dir():
            if payload.recursive:
                shutil.rmtree(target)
            else:
                target.rmdir()
        else:
            target.unlink()
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    return {"status": "ok"}


def get_stt_model():
    global _stt_model
    if _stt_model is not None:
        return _stt_model
    with _stt_lock:
        if _stt_model is not None:
            return _stt_model
        try:
            from faster_whisper import WhisperModel
        except ImportError as exc:  # pragma: no cover - runtime guard
            raise RuntimeError(
                "faster-whisper is not installed. Install it in apps/backend/.venv."
            ) from exc
        _stt_model = WhisperModel(
            STT_MODEL_NAME,
            device=STT_DEVICE,
            compute_type=STT_COMPUTE_TYPE,
        )
        return _stt_model


def transcribe_file(path: str, language: Optional[str]):
    model = get_stt_model()
    segments, info = model.transcribe(
        path,
        language=language or None,
        beam_size=1,
        best_of=1,
        vad_filter=True,
    )
    text = "".join(segment.text for segment in segments).strip()
    return text, info


@app.post("/stt")
async def stt(file: UploadFile = File(...), language: Optional[str] = Form(None)):
    if file is None:
        raise HTTPException(status_code=400, detail="Missing audio file")

    suffix = Path(file.filename or "audio").suffix or ".m4a"
    tmp_path = None
    start = time.monotonic()
    try:
        logger.info("STT request: filename=%s content_type=%s", file.filename, file.content_type)
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
            tmp.write(await file.read())
            tmp_path = tmp.name

        text, info = await asyncio.to_thread(transcribe_file, tmp_path, language)
        elapsed = time.monotonic() - start
        logger.info("STT done in %.2fs (%d chars)", elapsed, len(text))
        return {
            "text": text,
            "language": getattr(info, "language", None),
            "language_probability": getattr(info, "language_probability", None),
            "duration": getattr(info, "duration", None),
        }
    except RuntimeError as exc:
        logger.exception("STT runtime error")
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    except Exception as exc:
        logger.exception("STT failed")
        raise HTTPException(status_code=500, detail=f"STT failed: {exc}") from exc
    finally:
        if tmp_path:
            try:
                Path(tmp_path).unlink()
            except FileNotFoundError:
                pass


@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()

    cwd_override = websocket.query_params.get("cwd")
    cmd, cwd, err = resolve_codex_command(cwd_override=cwd_override)
    if err:
        await websocket.send_text(f"Error: {err}\r\n")
        await websocket.close(code=1011)
        return

    session = HeadlessPTY(cmd, cwd, build_env_with_path(cmd[0] if cmd else None))
    try:
        session.start()
    except Exception as exc:
        await websocket.send_text(f"Error starting codex: {exc}\r\n")
        await websocket.close(code=1011)
        return

    async def pty_reader():
        loop = asyncio.get_running_loop()
        pending = b""
        while session.running:
            try:
                data = await loop.run_in_executor(None, session.read, 4096)
                if not data:
                    break
                data = pending + data
                pending = b""
                if data.endswith(b"\x1b"):
                    pending = b"\x1b"
                    data = data[:-1]
                elif data.endswith(b"\x1b["):
                    pending = b"\x1b["
                    data = data[:-2]
                elif data.endswith(b"\x1b[6"):
                    pending = b"\x1b[6"
                    data = data[:-3]

                count = data.count(CURSOR_POS_QUERY)
                if count:
                    for _ in range(count):
                        session.write(b"\x1b[1;1R")
                    data = data.replace(CURSOR_POS_QUERY, b"")

                if data:
                    try:
                        await websocket.send_text(data.decode("utf-8", errors="ignore"))
                    except RuntimeError:
                        break
            except Exception:
                break
        await websocket.close()

    reader_task = asyncio.create_task(pty_reader())

    try:
        while True:
            data = await websocket.receive_text()
            if data:
                session.write(data.encode("utf-8"))
    except WebSocketDisconnect:
        pass
    except Exception:
        pass
    finally:
        session.stop()
        reader_task.cancel()


def resolve_codex_command(cwd_override: Optional[str] = None) -> tuple[list[str], Path, Optional[str]]:
    config = load_config()

    if cwd_override:
        override_path = Path(cwd_override).expanduser()
        if not override_path.exists() or not override_path.is_dir():
            return [], Path.home(), f"Working directory not found: {cwd_override}"
        cwd = override_path
    else:
        workdir = os.environ.get("CODEX_WORKDIR") or (
            config.get("terminal", {}).get("working_directory") if config else None
        )
        if not workdir:
            workdir = "~"
        cwd = Path(workdir).expanduser()
        if not cwd.exists():
            cwd = Path.home()

    cmd_override = os.environ.get("CODEX_CMD")
    if cmd_override:
        return shlex.split(cmd_override), cwd, None

    codex_path = os.environ.get("CODEX_PATH") or (
        config.get("terminal", {}).get("codex_path") if config else None
    )
    if not codex_path:
        codex_path = "codex"

    codex_args_env = os.environ.get("CODEX_ARGS")
    if codex_args_env:
        codex_args = shlex.split(codex_args_env)
    else:
        codex_args = config.get("terminal", {}).get("codex_args", []) if config else []

    resolved = resolve_codex_path(codex_path)
    if not resolved:
        if os.environ.get("CODEX_ALLOW_SHELL_FALLBACK") == "1":
            return ["/bin/bash"], cwd, None
        return [], cwd, (
            "Codex binary not found. Set codex_path in ~/.config/codex-stt-assistant/config.json "
            "or set CODEX_PATH/CODEX_CMD."
        )

    return [resolved] + codex_args, cwd, None


def load_config() -> dict:
    if not CONFIG_PATH.exists():
        return {}
    try:
        with CONFIG_PATH.open("r", encoding="utf-8") as handle:
            return json.load(handle)
    except Exception:
        return {}


def resolve_codex_path(codex_path: str) -> Optional[str]:
    candidate = Path(codex_path).expanduser()
    if candidate.is_absolute() and candidate.exists():
        return str(candidate)

    found = shutil.which(str(codex_path))
    if found:
        return found

    nvm_candidates = list(Path.home().glob(".nvm/versions/node/*/bin/codex"))
    if nvm_candidates:
        return str(pick_latest_nvm(nvm_candidates))

    return None


def pick_latest_nvm(paths: list[Path]) -> Path:
    def key(path: Path):
        name = path.parents[1].name
        match = re.findall(r"\d+", name)
        if not match:
            return (0,)
        return tuple(int(x) for x in match)

    return sorted(paths, key=key, reverse=True)[0]


def build_env_with_path(exec_path: Optional[str]) -> dict[str, str]:
    env = os.environ.copy()
    env.setdefault("TERM", "xterm-256color")
    if exec_path:
        codex_dir = str(Path(exec_path).parent)
        current_path = env.get("PATH", "")
        if codex_dir and codex_dir not in current_path.split(":"):
            env["PATH"] = f"{codex_dir}:{current_path}" if current_path else codex_dir
    return env
