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
from fastapi.responses import Response
from pydantic import BaseModel

app = FastAPI()
logger = logging.getLogger("codex-backend")

CONFIG_PATH = Path(
    os.environ.get("CODEX_CONFIG", "~/.config/codex-stt-assistant/config.json")
).expanduser()
REPO_ROOT = Path(__file__).resolve().parents[2]
LIVE_HELPER_PACKAGE = "com.meinzeug.codexspeech.viewer.live"
LIVE_HELPER_APK = REPO_ROOT / "apps" / "android-viewer-live" / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
VIEWER_APK = REPO_ROOT / "apps" / "android-viewer" / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
GRADLE_VIEWER = REPO_ROOT / "apps" / "android-viewer" / "gradle-8.5" / "bin" / "gradle"
GRADLE_LIVE = REPO_ROOT / "apps" / "android-viewer" / "gradle-8.5" / "bin" / "gradle"
ANDROID_VIEWER_DIR = REPO_ROOT / "apps" / "android-viewer"
ANDROID_LIVE_DIR = REPO_ROOT / "apps" / "android-viewer-live"

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


class RunnerScanRequest(BaseModel):
    path: Optional[str] = None
    depth: int = 2


class RunnerRnHostRequest(BaseModel):
    host: str
    port: int = 8081
    package: Optional[str] = None
    device_id: Optional[str] = None
    path: Optional[str] = None


class RunnerRnReloadRequest(BaseModel):
    device_id: Optional[str] = None


class RunnerOpenRequest(BaseModel):
    device_id: Optional[str] = None
    package: Optional[str] = None
    path: Optional[str] = None


class LiveTapRequest(BaseModel):
    device_id: Optional[str] = None
    x: int
    y: int


class LiveSwipeRequest(BaseModel):
    device_id: Optional[str] = None
    x1: int
    y1: int
    x2: int
    y2: int
    duration_ms: int = 300


class LiveTextRequest(BaseModel):
    device_id: Optional[str] = None
    text: str


class LiveKeyRequest(BaseModel):
    device_id: Optional[str] = None
    keycode: int


class SettingsPayload(BaseModel):
    terminal: Optional[dict] = None
    stt: Optional[dict] = None


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


def detect_android_package(cwd: Path) -> Optional[str]:
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
        match = re.search(r'applicationId\s*[= ]\s*["\']([^"\']+)["\']', content)
        if match:
            return match.group(1)
        match = re.search(r'namespace\s*[= ]\s*["\']([^"\']+)["\']', content)
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


def detect_rn_package(cwd: Path) -> Optional[str]:
    return detect_android_package(cwd)


def scan_projects(base: Path, max_depth: int) -> list[dict]:
    max_depth = max(0, min(max_depth, 8))
    results: list[dict] = []
    skip_names = {
        ".git",
        ".idea",
        ".vscode",
        ".gradle",
        "node_modules",
        "build",
        "dist",
        ".venv",
        "venv",
        "__pycache__",
        ".cache",
        ".android",
        ".flutter-plugins",
        ".flutter-plugins-dependencies",
        "android/.gradle",
        "ios/Pods",
    }
    queue: list[tuple[Path, int]] = [(base, 0)]
    seen: set[Path] = set()

    while queue:
        current, depth = queue.pop(0)
        if current in seen:
            continue
        seen.add(current)
        if not current.exists() or not current.is_dir():
            continue

        project_type = detect_project_type(current)
        if project_type:
            package = detect_android_package(current) if project_type in ("react-native", "flutter") else None
            results.append(
                {
                    "path": str(current),
                    "project_type": project_type,
                    "android_package": package,
                }
            )
            # Do not descend into a detected project root
            continue

        if depth >= max_depth:
            continue

        try:
            for child in current.iterdir():
                if not child.is_dir():
                    continue
                if child.name in skip_names:
                    continue
                # Skip symlinks to avoid cycles
                if child.is_symlink():
                    continue
                queue.append((child, depth + 1))
        except PermissionError:
            continue
    return results


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


def run_adb_binary(args: list[str]) -> bytes:
    try:
        result = subprocess.run(
            ["adb"] + args,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=True,
        )
        return result.stdout
    except FileNotFoundError as exc:
        raise HTTPException(status_code=500, detail="adb not found in PATH") from exc
    except subprocess.CalledProcessError as exc:
        output = exc.stdout.decode("utf-8", errors="ignore") if exc.stdout else "adb command failed"
        raise HTTPException(status_code=500, detail=output.strip()) from exc


def adb_escape_text(value: str) -> str:
    # adb input text expects no spaces; use %s for spaces.
    return value.replace(" ", "%s")


def run_command(args: list[str], cwd: Optional[Path] = None, timeout: int = 900) -> str:
    try:
        result = subprocess.run(
            args,
            cwd=str(cwd) if cwd else None,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=True,
            timeout=timeout,
        )
        return result.stdout.strip()
    except FileNotFoundError as exc:
        raise HTTPException(status_code=500, detail=f"Command not found: {args[0]}") from exc
    except subprocess.TimeoutExpired as exc:
        output = exc.stdout or ""
        raise HTTPException(status_code=500, detail=f"Command timed out. Output:\n{output}") from exc
    except subprocess.CalledProcessError as exc:
        raise HTTPException(status_code=500, detail=exc.stdout.strip() or "Command failed") from exc


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
    apply_stt_settings(load_config())
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
def runner_detect(path: Optional[str] = None, depth: int = 0):
    cwd = resolve_workdir(path)
    if not cwd.exists() or not cwd.is_dir():
        raise HTTPException(status_code=404, detail=f"Working directory not found: {cwd}")
    if depth > 0:
        projects = scan_projects(cwd, depth)
        if len(projects) == 1:
            project = projects[0]
            return {
                "cwd": project["path"],
                "project_type": project["project_type"],
                "android_package": project["android_package"],
                "projects": projects,
            }
        return {
            "cwd": str(cwd),
            "project_type": None,
            "android_package": None,
            "projects": projects,
        }
    project_type = detect_project_type(cwd)
    package = None
    if project_type in ("react-native", "flutter"):
        package = detect_android_package(cwd)
    return {"cwd": str(cwd), "project_type": project_type, "android_package": package}


@app.get("/runner/scan")
def runner_scan(path: Optional[str] = None, depth: int = 2):
    cwd = resolve_workdir(path)
    if not cwd.exists() or not cwd.is_dir():
        raise HTTPException(status_code=404, detail=f"Working directory not found: {cwd}")
    projects = scan_projects(cwd, depth)
    return {"base": str(cwd), "depth": depth, "projects": projects}


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


@app.post("/runner/open")
def runner_open(payload: RunnerOpenRequest):
    device_id = resolve_device_id(payload.device_id)
    package = payload.package
    if not package:
        cwd = None
        if payload.path:
            cwd = resolve_workdir(payload.path)
        elif RUNNER.cwd:
            cwd = RUNNER.cwd
        if cwd:
            package = detect_android_package(cwd)
    if not package:
        raise HTTPException(status_code=400, detail="Android package not detected. Select a project or set package.")
    try:
        run_adb(
            [
                "-s",
                device_id,
                "shell",
                "monkey",
                "-p",
                package,
                "-c",
                "android.intent.category.LAUNCHER",
                "1",
            ]
        )
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    return {"status": "ok", "package": package}


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
        package = detect_android_package(cwd)
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


@app.get("/live/devices")
def live_devices():
    return {"devices": list_adb_devices()}


@app.get("/live/snapshot")
def live_snapshot(device_id: Optional[str] = None, format: str = "png", quality: int = 70):
    resolved = resolve_device_id(device_id)
    image = run_adb_binary(["-s", resolved, "exec-out", "screencap", "-p"])
    if not image:
        raise HTTPException(status_code=500, detail="Empty screenshot")
    fmt = (format or "png").lower()
    if fmt in ("jpg", "jpeg"):
        try:
            q = max(2, min(31, int(round(31 - (max(1, min(100, quality)) / 100) * 29))))
            result = subprocess.run(
                [
                    "ffmpeg",
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-i",
                    "pipe:0",
                    "-vframes",
                    "1",
                    "-q:v",
                    str(q),
                    "pipe:1",
                ],
                input=image,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=True,
            )
            image = result.stdout
        except FileNotFoundError as exc:
            raise HTTPException(status_code=500, detail="ffmpeg not found for JPEG conversion") from exc
        except subprocess.CalledProcessError as exc:
            raise HTTPException(status_code=500, detail=exc.stderr.decode("utf-8", errors="ignore")) from exc
        return Response(content=image, media_type="image/jpeg")
    return Response(content=image, media_type="image/png")


@app.post("/live/install")
def live_install(device_id: Optional[str] = None):
    resolved = resolve_device_id(device_id)
    if not LIVE_HELPER_APK.exists():
        raise HTTPException(status_code=404, detail=f"Live helper APK not found: {LIVE_HELPER_APK}")
    run_adb(["-s", resolved, "install", "-r", str(LIVE_HELPER_APK)])
    return {"status": "ok", "apk": str(LIVE_HELPER_APK)}


@app.post("/live/open")
def live_open(device_id: Optional[str] = None):
    resolved = resolve_device_id(device_id)
    run_adb(
        [
            "-s",
            resolved,
            "shell",
            "monkey",
            "-p",
            LIVE_HELPER_PACKAGE,
            "-c",
            "android.intent.category.LAUNCHER",
            "1",
        ]
    )
    return {"status": "ok", "package": LIVE_HELPER_PACKAGE}


@app.post("/live/tap")
def live_tap(payload: LiveTapRequest):
    resolved = resolve_device_id(payload.device_id)
    run_adb(["-s", resolved, "shell", "input", "tap", str(payload.x), str(payload.y)])
    return {"status": "ok"}


@app.post("/live/swipe")
def live_swipe(payload: LiveSwipeRequest):
    resolved = resolve_device_id(payload.device_id)
    duration = payload.duration_ms if payload.duration_ms > 0 else 300
    run_adb(
        [
            "-s",
            resolved,
            "shell",
            "input",
            "swipe",
            str(payload.x1),
            str(payload.y1),
            str(payload.x2),
            str(payload.y2),
            str(duration),
        ]
    )
    return {"status": "ok"}


@app.post("/live/longpress")
def live_longpress(payload: LiveTapRequest):
    resolved = resolve_device_id(payload.device_id)
    run_adb(
        [
            "-s",
            resolved,
            "shell",
            "input",
            "swipe",
            str(payload.x),
            str(payload.y),
            str(payload.x),
            str(payload.y),
            "600",
        ]
    )
    return {"status": "ok"}


@app.post("/live/text")
def live_text(payload: LiveTextRequest):
    resolved = resolve_device_id(payload.device_id)
    text = adb_escape_text(payload.text)
    run_adb(["-s", resolved, "shell", "input", "text", text])
    return {"status": "ok"}


@app.post("/live/key")
def live_key(payload: LiveKeyRequest):
    resolved = resolve_device_id(payload.device_id)
    run_adb(["-s", resolved, "shell", "input", "keyevent", str(payload.keycode)])
    return {"status": "ok"}


@app.post("/live/wake")
def live_wake(device_id: Optional[str] = None):
    resolved = resolve_device_id(device_id)
    try:
        run_adb(["-s", resolved, "shell", "input", "keyevent", "224"])
    except HTTPException:
        run_adb(["-s", resolved, "shell", "input", "keyevent", "26"])
    return {"status": "ok"}


@app.get("/")
def dashboard_page():
    return Response(content=DASHBOARD_HTML, media_type="text/html")


@app.get("/api/settings")
def api_settings():
    config = load_config()
    terminal = config.get("terminal", {}) if config else {}
    stt_cfg = config.get("stt", {}) if config else {}
    return {
        "terminal": {
            "working_directory": terminal.get("working_directory", ""),
            "codex_path": terminal.get("codex_path", ""),
            "codex_args": terminal.get("codex_args", []),
        },
        "stt": {
            "model": stt_cfg.get("model", STT_MODEL_NAME),
            "device": stt_cfg.get("device", STT_DEVICE),
            "compute_type": stt_cfg.get("compute_type", STT_COMPUTE_TYPE),
        },
        "config_path": str(CONFIG_PATH),
        "env": {
            "STT_MODEL": os.environ.get("STT_MODEL"),
            "STT_DEVICE": os.environ.get("STT_DEVICE"),
            "STT_COMPUTE_TYPE": os.environ.get("STT_COMPUTE_TYPE"),
        },
    }


@app.post("/api/settings")
def api_settings_update(payload: SettingsPayload):
    config = load_config()
    if not config:
        config = {}
    terminal = config.setdefault("terminal", {})
    stt_cfg = config.setdefault("stt", {})

    if payload.terminal:
        if "working_directory" in payload.terminal:
            terminal["working_directory"] = (payload.terminal.get("working_directory") or "").strip()
        if "codex_path" in payload.terminal:
            terminal["codex_path"] = (payload.terminal.get("codex_path") or "").strip()
        if "codex_args" in payload.terminal:
            args_value = payload.terminal.get("codex_args")
            if isinstance(args_value, str):
                terminal["codex_args"] = shlex.split(args_value.strip()) if args_value.strip() else []
            elif isinstance(args_value, list):
                terminal["codex_args"] = [str(item) for item in args_value if str(item).strip()]

    if payload.stt:
        if "model" in payload.stt:
            stt_cfg["model"] = (payload.stt.get("model") or "").strip()
        if "device" in payload.stt:
            stt_cfg["device"] = (payload.stt.get("device") or "").strip()
        if "compute_type" in payload.stt:
            stt_cfg["compute_type"] = (payload.stt.get("compute_type") or "").strip()

    save_config(config)
    apply_stt_settings(config)
    return {"status": "ok", "config_path": str(CONFIG_PATH)}


@app.get("/settings")
def settings_page():
    html = f"""
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Codex Speech Settings</title>
  <style>
    body {{ font-family: ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, sans-serif; background: #f5f7fb; color: #111827; margin: 0; }}
    .wrap {{ max-width: 860px; margin: 32px auto; padding: 0 16px; }}
    .card {{ background: #fff; border-radius: 16px; padding: 24px; box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08); }}
    h1 {{ margin: 0 0 8px; font-size: 24px; }}
    .muted {{ color: #6b7280; font-size: 14px; }}
    label {{ display: block; margin: 16px 0 6px; font-weight: 600; }}
    input {{ width: 100%; padding: 10px 12px; border-radius: 10px; border: 1px solid #d1d5db; }}
    .row {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 16px; }}
    button {{ margin-top: 18px; padding: 10px 14px; border-radius: 10px; border: none; background: #2563eb; color: #fff; font-weight: 600; cursor: pointer; }}
    .status {{ margin-top: 12px; font-size: 14px; }}
    code {{ background: #f3f4f6; padding: 2px 6px; border-radius: 6px; }}
  </style>
</head>
<body>
  <div class="wrap">
    <div class="card">
      <h1>Codex Speech Settings</h1>
      <div class="muted">Config file: <code>{CONFIG_PATH}</code></div>

      <h3>Terminal</h3>
      <label>Working directory</label>
      <input id="working_directory" placeholder="/home/user/projects" />
      <label>Codex path</label>
      <input id="codex_path" placeholder="codex or /path/to/codex" />
      <label>Codex args (space separated)</label>
      <input id="codex_args" placeholder="--model gpt-4o-mini" />

      <h3>STT (faster-whisper)</h3>
      <div class="row">
        <div>
          <label>Model</label>
          <input id="stt_model" placeholder="small / medium / large-v3" />
        </div>
        <div>
          <label>Device</label>
          <input id="stt_device" placeholder="cpu / cuda" />
        </div>
        <div>
          <label>Compute type</label>
          <input id="stt_compute" placeholder="int8 / int8_float16" />
        </div>
      </div>
      <div class="muted">Note: If STT env vars are set, they override config.</div>

      <button id="save">Save Settings</button>
      <div class="status" id="status"></div>
    </div>
  </div>
  <script>
    async function loadSettings() {{
      const res = await fetch('/api/settings');
      const data = await res.json();
      document.getElementById('working_directory').value = data.terminal.working_directory || '';
      document.getElementById('codex_path').value = data.terminal.codex_path || '';
      document.getElementById('codex_args').value = (data.terminal.codex_args || []).join(' ');
      document.getElementById('stt_model').value = data.stt.model || '';
      document.getElementById('stt_device').value = data.stt.device || '';
      document.getElementById('stt_compute').value = data.stt.compute_type || '';
    }}
    async function saveSettings() {{
      const payload = {{
        terminal: {{
          working_directory: document.getElementById('working_directory').value,
          codex_path: document.getElementById('codex_path').value,
          codex_args: document.getElementById('codex_args').value
        }},
        stt: {{
          model: document.getElementById('stt_model').value,
          device: document.getElementById('stt_device').value,
          compute_type: document.getElementById('stt_compute').value
        }}
      }};
      const res = await fetch('/api/settings', {{
        method: 'POST',
        headers: {{ 'Content-Type': 'application/json' }},
        body: JSON.stringify(payload)
      }});
      const data = await res.json();
      const status = document.getElementById('status');
      status.textContent = res.ok ? 'Saved.' : (data.detail || 'Error saving settings');
    }}
    document.getElementById('save').addEventListener('click', saveSettings);
    loadSettings();
  </script>
</body>
</html>
"""
    return Response(content=html, media_type="text/html")


@app.get("/api/admin/status")
def admin_status():
    return {
        "repo_root": str(REPO_ROOT),
        "viewer_apk": str(VIEWER_APK),
        "live_apk": str(LIVE_HELPER_APK),
        "devices": list_adb_devices(),
        "backend_port": os.environ.get("CODEX_BACKEND_PORT"),
        "settings_port": os.environ.get("CODEX_SETTINGS_PORT"),
    }


@app.post("/api/admin/git-pull")
def admin_git_pull():
    output = run_command(["git", "-C", str(REPO_ROOT), "pull", "--ff-only"])
    return {"status": "ok", "output": output}


@app.post("/api/admin/build")
def admin_build(target: str = "viewer"):
    if target == "viewer":
        if not GRADLE_VIEWER.exists():
            raise HTTPException(status_code=404, detail=f"Gradle not found at {GRADLE_VIEWER}")
        output = run_command([str(GRADLE_VIEWER), "-p", str(ANDROID_VIEWER_DIR), ":app:assembleDebug"])
        return {"status": "ok", "output": output}
    if target == "live":
        if not GRADLE_LIVE.exists():
            raise HTTPException(status_code=404, detail=f"Gradle not found at {GRADLE_LIVE}")
        output = run_command([str(GRADLE_LIVE), "-p", str(ANDROID_LIVE_DIR), ":app:assembleDebug"])
        return {"status": "ok", "output": output}
    raise HTTPException(status_code=400, detail="Unknown build target")


@app.post("/api/admin/install")
def admin_install(target: str = "viewer", device_id: Optional[str] = None):
    resolved = resolve_device_id(device_id)
    if target == "viewer":
        if not VIEWER_APK.exists():
            raise HTTPException(status_code=404, detail=f"Viewer APK not found: {VIEWER_APK}")
        run_adb(["-s", resolved, "install", "-r", str(VIEWER_APK)])
        return {"status": "ok", "apk": str(VIEWER_APK)}
    if target == "live":
        if not LIVE_HELPER_APK.exists():
            raise HTTPException(status_code=404, detail=f"Live APK not found: {LIVE_HELPER_APK}")
        run_adb(["-s", resolved, "install", "-r", str(LIVE_HELPER_APK)])
        return {"status": "ok", "apk": str(LIVE_HELPER_APK)}
    raise HTTPException(status_code=400, detail="Unknown install target")


@app.post("/api/admin/pm2-restart")
def admin_pm2_restart(target: str = "codex-backend"):
    if not shutil.which("pm2"):
        raise HTTPException(status_code=500, detail="pm2 not found")
    output = run_command(["pm2", "restart", target, "--update-env"])
    return {"status": "ok", "output": output}


DASHBOARD_HTML = f"""
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Codex Speech Dashboard</title>
  <style>
    body {{ font-family: ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, sans-serif; background: #0f172a; color: #e2e8f0; margin: 0; }}
    .wrap {{ max-width: 980px; margin: 32px auto; padding: 0 16px; }}
    .grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 16px; }}
    .card {{ background: #111827; border: 1px solid #1f2937; border-radius: 16px; padding: 18px; box-shadow: 0 10px 20px rgba(0,0,0,0.25); }}
    h1 {{ margin: 0 0 8px; font-size: 24px; }}
    h2 {{ margin: 0 0 12px; font-size: 18px; }}
    .muted {{ color: #94a3b8; font-size: 13px; }}
    button {{ padding: 10px 12px; border: none; border-radius: 10px; background: #2563eb; color: #fff; font-weight: 600; cursor: pointer; }}
    button.secondary {{ background: #334155; }}
    button.danger {{ background: #dc2626; }}
    select, input {{ width: 100%; padding: 8px 10px; border-radius: 10px; border: 1px solid #334155; background: #0f172a; color: #e2e8f0; }}
    pre {{ background: #0b1220; padding: 12px; border-radius: 10px; overflow: auto; max-height: 180px; }}
    .row {{ display: flex; gap: 8px; flex-wrap: wrap; }}
    a {{ color: #60a5fa; text-decoration: none; }}
  </style>
</head>
<body>
  <div class="wrap">
    <h1>Codex Speech Dashboard</h1>
    <div class="muted">Backend: <code>{os.environ.get("CODEX_BACKEND_PORT") or "17500"}</code> · Settings UI: <code>{os.environ.get("CODEX_SETTINGS_PORT") or "17000"}</code></div>
    <div class="grid" style="margin-top:16px;">
      <div class="card">
        <h2>Repo</h2>
        <div class="row">
          <button onclick="runAction('/api/admin/git-pull')">Git Pull</button>
          <button class="secondary" onclick="openSettings()">Open Settings UI</button>
        </div>
      </div>
      <div class="card">
        <h2>Android Builds</h2>
        <div class="row">
          <button onclick="runAction('/api/admin/build?target=viewer')">Build Viewer</button>
          <button onclick="runAction('/api/admin/build?target=live')">Build Live APK</button>
        </div>
      </div>
      <div class="card">
        <h2>ADB Devices</h2>
        <select id="deviceSelect"></select>
        <div class="row" style="margin-top:10px;">
          <button onclick="runInstall('viewer')">Install Viewer</button>
          <button onclick="runInstall('live')">Install Live</button>
        </div>
      </div>
      <div class="card">
        <h2>PM2</h2>
        <div class="row">
          <button class="secondary" onclick="runAction('/api/admin/pm2-restart?target=codex-backend')">Restart Backend</button>
          <button class="secondary" onclick="runAction('/api/admin/pm2-restart?target=codex-web')">Restart Web</button>
        </div>
      </div>
    </div>
    <div class="card" style="margin-top:16px;">
      <h2>Output</h2>
      <pre id="output"></pre>
    </div>
  </div>
  <script>
    async function loadDevices() {{
      const res = await fetch('/api/admin/status');
      const data = await res.json();
      const sel = document.getElementById('deviceSelect');
      sel.innerHTML = '';
      (data.devices || []).forEach(d => {{
        const opt = document.createElement('option');
        const label = (d.model || d.device || 'Device') + ' (' + d.id + ')';
        opt.value = d.id;
        opt.textContent = label;
        sel.appendChild(opt);
      }});
      if (!sel.options.length) {{
        const opt = document.createElement('option');
        opt.textContent = 'No devices';
        opt.value = '';
        sel.appendChild(opt);
      }}
    }}
    async function runAction(url) {{
      const out = document.getElementById('output');
      out.textContent = 'Running...';
      const res = await fetch(url, {{ method: 'POST' }});
      const text = await res.text();
      out.textContent = text;
      loadDevices();
    }}
    async function runInstall(target) {{
      const sel = document.getElementById('deviceSelect');
      const device = sel.value || '';
      const url = `/api/admin/install?target=${{target}}&device_id=${{encodeURIComponent(device)}}`;
      await runAction(url);
    }}
    function openSettings() {{
      window.open('/settings', '_blank');
    }}
    loadDevices();
  </script>
</body>
</html>
"""


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


def save_config(config: dict) -> None:
    CONFIG_PATH.parent.mkdir(parents=True, exist_ok=True)
    with CONFIG_PATH.open("w", encoding="utf-8") as handle:
        json.dump(config, handle, indent=2, ensure_ascii=False)


def apply_stt_settings(config: dict) -> None:
    global STT_MODEL_NAME, STT_DEVICE, STT_COMPUTE_TYPE, _stt_model
    stt_cfg = config.get("stt", {}) if config else {}
    updated = False
    if "STT_MODEL" not in os.environ:
        model = stt_cfg.get("model")
        if model and model != STT_MODEL_NAME:
            STT_MODEL_NAME = model
            updated = True
    if "STT_DEVICE" not in os.environ:
        device = stt_cfg.get("device")
        if device and device != STT_DEVICE:
            STT_DEVICE = device
            updated = True
    if "STT_COMPUTE_TYPE" not in os.environ:
        compute = stt_cfg.get("compute_type")
        if compute and compute != STT_COMPUTE_TYPE:
            STT_COMPUTE_TYPE = compute
            updated = True
    if updated:
        _stt_model = None


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
