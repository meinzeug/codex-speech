#!/bin/bash
set -euo pipefail

APP_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )"

if command -v xvfb-run >/dev/null 2>&1; then
  RUNNER=(xvfb-run -a)
else
  RUNNER=()
fi

export CODEX_STT_TEST_MODE=1
export CODEX_STT_NO_TERMINAL=1

source "$APP_DIR/venv/bin/activate"
PYTHONPATH="$APP_DIR/src" "${RUNNER[@]}" python3 "$APP_DIR/src/main.py" --self-test --no-terminal
