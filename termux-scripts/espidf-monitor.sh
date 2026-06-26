#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
PROJECT="${1:-$HOME/ESP32-RoboEyes}"
TTY="${TTY:-$TMPDIR/ttyesp32}"
PORT="${PORT:-6667}"

mkdir -p "$(dirname "$TTY")"
socat -d -d pty,raw,echo=0,link="$TTY" tcp:127.0.0.1:"$PORT" &
SOCAT_PID=$!
trap 'kill $SOCAT_PID >/dev/null 2>&1 || true' EXIT

cd "$PROJECT"
source ~/esp-idf/export.sh
idf.py -p "$TTY" -b 115200 monitor
