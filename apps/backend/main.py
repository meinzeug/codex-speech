from __future__ import annotations

import asyncio
import base64
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
from collections import deque
from dataclasses import dataclass, field
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


class RunnerStartRequest(BaseModel):
    path: Optional[str] = None
    project_type: Optional[str] = None
    device_id: Optional[str] = None
    mode: str = "adb"
    metro_port: int = 8081


class RunnerReloadRequest(BaseModel):
    type: str = "hot"


class RunnerRnHostRequest(BaseModel):
    host: str
    port: int = 8081
    package: Optional[str] = None
    device_id: Optional[str] = None
    path: Optional[str] = None


class RunnerRnReloadRequest(BaseModel):
    device_id: Optional[str] = None


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


@dataclass
class RunnerProcess:
    name: str
    command: list[str]
    cwd: Path
    env: dict[str, str]
    output: deque[str] = field(default_factory=lambda: deque(maxlen=400))
    process: Optional[subprocess.Popen] = None
    started_at: Optional[float] = None
    exited_at: Optional[float] = None
    exit_code: Optional[int] = None
    _reader: Optional[threading.Thread] = None
    _lock: threading.Lock = field(default_factory=threading.Lock)

    def start(self) -> None:
        with self._lock:
            if self.process and self.process.poll() is None:
                return
            self.started_at = time.time()
            self.exited_at = None
            self.exit_code = None
            self.process = subprocess.Popen(
                self.command,
                cwd=str(self.cwd),
                env=self.env,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
                universal_newlines=True,
            )
            self._reader = threading.Thread(target=self._read_output, daemon=True)
            self._reader.start()

    def _read_output(self) -> None:
        if not self.process or self.process.stdout is None:
            return
        try:
            for line in self.process.stdout:
                self.output.append(line.rstrip())
        finally:
            if self.process:
                self.exit_code = self.process.poll()
            self.exited_at = time.time()

    def write(self, data: str) -> None:
        with self._lock:
            if not self.process or self.process.stdin is None:
                return
            try:
                self.process.stdin.write(data)
                self.process.stdin.flush()
            except Exception:
                pass

    def stop(self, timeout: float = 4.0) -> None:
        with self._lock:
            if not self.process:
                return
            if self.process.poll() is None:
                self.process.terminate()
                try:
                    self.process.wait(timeout=timeout)
                except subprocess.TimeoutExpired:
                    self.process.kill()
            self.exit_code = self.process.poll()
            self.exited_at = time.time()


class RunnerManager:
    def __init__(self):
        self._lock = threading.Lock()
        self.project_type: Optional[str] = None
        self.cwd: Optional[Path] = None
        self.device_id: Optional[str] = None
        self.mode: Optional[str] = None
        self.metro_port: Optional[int] = None
        self.metro: Optional[RunnerProcess] = None
        self.app: Optional[RunnerProcess] = None
        self.flutter: Optional[RunnerProcess] = None
        self.last_error: Optional[str] = None

    def _env(self) -> dict[str, str]:
        env = os.environ.copy()
        env.setdefault("PYTHONUNBUFFERED", "1")
        return env

    def stop(self) -> None:
        with self._lock:
            for proc in (self.metro, self.app, self.flutter):
                if proc:
                    proc.stop()
            self.metro = None
            self.app = None
            self.flutter = None
            self.project_type = None
            self.cwd = None
            self.device_id = None
            self.mode = None
            self.metro_port = None
            self.last_error = None

    def status(self) -> dict:
        with self._lock:
            return {
                "project_type": self.project_type,
                "cwd": str(self.cwd) if self.cwd else None,
                "device_id": self.device_id,
                "mode": self.mode,
                "metro_port": self.metro_port,
                "metro_running": self.metro is not None and self.metro.process is not None and self.metro.process.poll() is None,
                "app_running": self.app is not None and self.app.process is not None and self.app.process.poll() is None,
                "flutter_running": self.flutter is not None and self.flutter.process is not None and self.flutter.process.poll() is None,
                "last_error": self.last_error,
            }

    def logs(self) -> dict:
        with self._lock:
            return {
                "metro": list(self.metro.output) if self.metro else [],
                "app": list(self.app.output) if self.app else [],
                "flutter": list(self.flutter.output) if self.flutter else [],
            }

    def start_react_native(self, cwd: Path, device_id: str, mode: str, metro_port: int) -> None:
        self.stop()
        self.project_type = "react-native"
        self.cwd = cwd
        self.device_id = device_id
        self.mode = mode
        self.metro_port = metro_port

        if mode == "adb":
            run_adb(["-s", device_id, "reverse", f"tcp:{metro_port}", f"tcp:{metro_port}"])

        self.metro = RunnerProcess(
            name="metro",
            command=["npx", "react-native", "start", "--port", str(metro_port)],
            cwd=cwd,
            env=self._env(),
        )
        self.metro.start()

        run_cmd = ["npx", "react-native", "run-android", "--no-packager", "--port", str(metro_port)]
        if device_id:
            run_cmd += ["--deviceId", device_id]
        self.app = RunnerProcess(
            name="app",
            command=run_cmd,
            cwd=cwd,
            env=self._env(),
        )
        self.app.start()

    def start_flutter(self, cwd: Path, device_id: str) -> None:
        self.stop()
        self.project_type = "flutter"
        self.cwd = cwd
        self.device_id = device_id

        self.flutter = RunnerProcess(
            name="flutter",
            command=["flutter", "run", "-d", device_id],
            cwd=cwd,
            env=self._env(),
        )
        self.flutter.start()

    def hot_reload(self) -> None:
        with self._lock:
            if self.project_type == "flutter" and self.flutter:
                self.flutter.write("r\n")

    def hot_restart(self) -> None:
        with self._lock:
            if self.project_type == "flutter" and self.flutter:
                self.flutter.write("R\n")

    def dev_menu(self, device_id: str) -> None:
        run_adb(["-s", device_id, "shell", "input", "keyevent", "82"])


RUNNER = RunnerManager()


def resolve_workdir(path_override: Optional[str]) -> Path:
    if path_override:
        candidate = expand_dir_path(path_override)
        return candidate
    config = load_config()
    workdir = os.environ.get("CODEX_WORKDIR") or (
        config.get("terminal", {}).get("working_directory") if config else None
    )
    if not workdir:
        workdir = "~"
    cwd = Path(workdir).expanduser()
    if not cwd.exists():
        cwd = Path.home()
    return cwd


def detect_project_type(cwd: Path) -> Optional[str]:
    if (cwd / "pubspec.yaml").exists():
        return "flutter"
    package_json = cwd / "package.json"
    if package_json.exists():
        try:
            data = json.loads(package_json.read_text(encoding="utf-8"))
        except Exception:
            return None
        deps = data.get("dependencies", {})
        dev_deps = data.get("devDependencies", {})
        if "react-native" in deps or "react-native" in dev_deps:
            return "react-native"
    return None


def detect_rn_package(cwd: Path) -> Optional[str]:
    gradle_files = [
        cwd / "android" / "app" / "build.gradle",
        cwd / "android" / "app" / "build.gradle.kts",
    ]
    for gradle_file in gradle_files:
        if not gradle_file.exists():
            continue
        try:
            content = gradle_file.read_text(encoding="utf-8")
        except Exception:
            continue
        match = re.search(r'applicationId\\s*[= ]\\s*["\\\']([^"\\\']+)["\\\']', content)
        if match:
            return match.group(1)
        match = re.search(r'namespace\\s*[= ]\\s*["\\\']([^"\\\']+)["\\\']', content)
        if match:
            return match.group(1)
    manifest = cwd / "android" / "app" / "src" / "main" / "AndroidManifest.xml"
    if manifest.exists():
        try:
            content = manifest.read_text(encoding="utf-8")
            match = re.search(r'<manifest[^>]+package=["\\\']([^"\\\']+)["\\\']', content)
            if match:
                return match.group(1)
        except Exception:
            pass
    return None


def run_adb(args: list[str]) -> str:
    try:
        result = subprocess.run(
            ["adb"] + args,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=True,
        )
        return result.stdout.strip()
    except FileNotFoundError as exc:
        raise HTTPException(status_code=500, detail="adb not found in PATH") from exc
    except subprocess.CalledProcessError as exc:
        raise HTTPException(status_code=500, detail=exc.stdout.strip() or "adb command failed") from exc


def list_adb_devices() -> list[dict]:
    output = run_adb(["devices", "-l"])
    devices = []
    for line in output.splitlines():
        line = line.strip()
        if not line or line.startswith("List of devices"):
            continue
        parts = line.split()
        if len(parts) < 2:
            continue
        serial = parts[0]
        state = parts[1]
        if state != "device":
            continue
        details = {"serial": serial}
        for part in parts[2:]:
            if ":" in part:
                key, value = part.split(":", 1)
                details[key] = value
        devices.append(
            {
                "id": serial,
                "model": details.get("model", ""),
                "product": details.get("product", ""),
                "device": details.get("device", ""),
                "transport_id": details.get("transport_id", ""),
            }
        )
    return devices


def resolve_device_id(device_id: Optional[str]) -> str:
    devices = list_adb_devices()
    if not devices:
        raise HTTPException(status_code=404, detail="No adb devices connected")
    if device_id:
        if any(device["id"] == device_id for device in devices):
            return device_id
        raise HTTPException(status_code=404, detail=f"Device not found: {device_id}")
    return devices[0]["id"]


def set_rn_debug_host(device_id: str, package: str, host: str, port: int) -> None:
    host_port = f"{host}:{port}"
    xml = (
        "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
        "<map>\n"
        f"    <string name=\"debug_http_host\">{host_port}</string>\n"
        "</map>\n"
    )
    encoded = base64.b64encode(xml.encode("utf-8")).decode("ascii")
    cmd = (
        "mkdir -p shared_prefs && "
        f"echo {encoded} | base64 -d > shared_prefs/com.facebook.react.devsupport.DevInternalSettings.xml"
    )
    run_adb(["-s", device_id, "shell", "run-as", package, "sh", "-c", cmd])


def rn_reload(device_id: str) -> None:
    run_adb(["-s", device_id, "shell", "input", "text", "RR"])



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


@app.get("/runner/detect")
def runner_detect(path: Optional[str] = None):
    cwd = resolve_workdir(path)
    if not cwd.exists() or not cwd.is_dir():
        raise HTTPException(status_code=404, detail=f"Working directory not found: {cwd}")
    project_type = detect_project_type(cwd)
    package = None
    if project_type == "react-native":
        package = detect_rn_package(cwd)
    return {"cwd": str(cwd), "project_type": project_type, "android_package": package}


@app.get("/runner/devices")
def runner_devices():
    return {"devices": list_adb_devices()}


@app.get("/runner/status")
def runner_status():
    return RUNNER.status()


@app.get("/runner/logs")
def runner_logs():
    return RUNNER.logs()


@app.post("/runner/start")
def runner_start(payload: RunnerStartRequest):
    cwd = resolve_workdir(payload.path)
    if not cwd.exists() or not cwd.is_dir():
        raise HTTPException(status_code=404, detail=f"Working directory not found: {cwd}")
    project_type = payload.project_type or detect_project_type(cwd)
    if project_type:
        project_type = project_type.lower().replace("_", "-")
        if project_type in ("reactnative", "rn"):
            project_type = "react-native"
    if not project_type:
        raise HTTPException(status_code=400, detail="Project type not detected. Set project_type explicitly.")
    device_id = resolve_device_id(payload.device_id)
    mode = (payload.mode or "adb").lower()
    metro_port = payload.metro_port or 8081
    try:
        if project_type == "react-native":
            RUNNER.start_react_native(cwd, device_id, mode, metro_port)
        elif project_type == "flutter":
            RUNNER.start_flutter(cwd, device_id)
        else:
            raise HTTPException(status_code=400, detail=f"Unsupported project type: {project_type}")
    except HTTPException:
        raise
    except Exception as exc:
        RUNNER.last_error = str(exc)
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    return RUNNER.status()


@app.post("/runner/stop")
def runner_stop():
    RUNNER.stop()
    return {"status": "stopped"}


@app.post("/runner/reload")
def runner_reload(payload: RunnerReloadRequest):
    if payload.type.lower() in ("hot", "reload"):
        RUNNER.hot_reload()
        return {"status": "ok", "type": "hot"}
    if payload.type.lower() in ("restart", "hot_restart"):
        RUNNER.hot_restart()
        return {"status": "ok", "type": "restart"}
    raise HTTPException(status_code=400, detail="Unknown reload type")


@app.post("/runner/rn/host")
def runner_rn_host(payload: RunnerRnHostRequest):
    if not payload.host:
        raise HTTPException(status_code=400, detail="Host is required")
    device_id = resolve_device_id(payload.device_id)
    package = payload.package
    if not package:
        cwd = resolve_workdir(payload.path)
        package = detect_rn_package(cwd)
    if not package:
        raise HTTPException(status_code=400, detail="React Native package not found")
    try:
        set_rn_debug_host(device_id, package, payload.host, payload.port)
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    return {"status": "ok", "package": package}


@app.post("/runner/rn/reload")
def runner_rn_reload(payload: RunnerRnReloadRequest):
    device_id = resolve_device_id(payload.device_id)
    try:
        rn_reload(device_id)
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    return {"status": "ok"}


@app.post("/runner/devmenu")
def runner_devmenu(device_id: Optional[str] = None):
    resolved = resolve_device_id(device_id)
    RUNNER.dev_menu(resolved)
    return {"status": "ok"}


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
