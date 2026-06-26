#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
PROJECT="${1:-$HOME/ESP32-RoboEyes}"

cd "$PROJECT"
source ~/esp-idf/export.sh
idf.py build
