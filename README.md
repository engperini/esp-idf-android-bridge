# ESP-IDF Android Bridge

ESP-IDF Android Bridge is an Android Studio + Kotlin app that acts as a **binary-safe USB serial bridge** for ESP32 development on Android.

It is designed for use with **Termux + Ubuntu proot + ESP-IDF**, so you can run `idf.py monitor`, `idf.py flash`, and related workflows while the Android app owns the USB serial connection.

## Features

- Connects to ESP32 / ESP32-S3 over USB OTG
- Supports common USB serial chipsets through `usb-serial-for-android`
  - CDC ACM
  - CH340
  - CP210x
  - FTDI
- Runs a raw TCP bridge on `127.0.0.1:6667`
- Accepts one TCP client at a time
- Forwards **binary data** in both directions without text conversion
- Supports DTR and RTS control
- Provides configurable DTR/RTS mapping
  - Invert DTR
  - Invert RTS
  - Swap DTR/RTS
  - Bootloader timing
  - Reset pulse duration
  - Baud rate
- Includes a separate control port on `127.0.0.1:6668`
- Uses a foreground service for long-running bridge sessions
- Shows live logs, byte counters, and USB/TCP state
- Includes optional Termux integration helpers for monitor / flash / build commands
- Includes a GitHub Actions workflow to build APKs and publish them as downloadable artifacts

## Downloading the app

If you do not want to build locally, the easiest path is to use **GitHub Actions artifacts**:

1. Push a commit to `main` or run the workflow manually from the Actions tab
2. Open the workflow run in GitHub
3. Download the APK artifact from the run page

Artifacts produced by the workflow:

- `esp-idf-android-bridge-debug-apk`
- `esp-idf-android-bridge-release-apk` for tagged releases (`v*`)

## What this app is for

The main goal is to let an Android phone or tablet act as the USB serial backend for an ESP-IDF workflow.

Typical usage:

1. Android app connects to the ESP32 board over USB OTG
2. Termux connects to `127.0.0.1:6667`
3. ESP-IDF tools run inside Ubuntu proot
4. The app forwards raw bytes between USB and TCP

This is especially useful when you want to keep ESP-IDF on Android instead of on a desktop computer.

## Important limitation

Android apps cannot always directly launch Termux commands because of sandbox restrictions and differences between Termux plugins.

Because of that, this project supports two paths:

1. **Best-effort intent launch** for Termux / Termux:Tasker style integrations
2. **Copyable shell scripts** that you can run manually inside Termux

If direct launch does not work on your device, the bridge still works normally.

## Project structure

Important files:

- `app/src/main/java/com/example/espidfandroidbridge/MainActivity.kt`
- `app/src/main/java/com/example/espidfandroidbridge/BridgeApplication.kt`
- `app/src/main/java/com/example/espidfandroidbridge/bridge/BridgeService.kt`
- `app/src/main/java/com/example/espidfandroidbridge/bridge/UsbBridgeController.kt`
- `app/src/main/java/com/example/espidfandroidbridge/bridge/TermuxIntegration.kt`
- `app/src/main/java/com/example/espidfandroidbridge/bridge/BridgeStateStore.kt`
- `app/src/main/java/com/example/espidfandroidbridge/bridge/BridgePreferences.kt`
- `termux-scripts/espidf-monitor.sh`
- `termux-scripts/espidf-flash.sh`
- `termux-scripts/espidf-build.sh`
- `.github/workflows/android-build.yml`

## Screens

### 1. Main screen

- USB status
- TCP status
- Baud rate selector
- Connect USB button
- Start server button
- RX/TX counters
- DTR/RTS toggles
- Reset button
- Bootloader button

### 2. ESP-IDF screen

- Project path field
- Monitor command button
- Flash command button
- Build command button
- Output console
- Copy command button

### 3. Settings screen

- TCP port
- Baud rate
- DTR inversion
- RTS inversion
- DTR/RTS swap
- Bootloader timing
- Reset pulse duration
- Selected USB driver info
- USB device info

## Termux requirements

In Termux, install the basic tools used by the helper scripts:

```sh
pkg update
pkg install socat netcat-openbsd
```

Make sure you already have:

- Ubuntu proot installed
- ESP-IDF installed inside Ubuntu
- Your ESP32 project available in a known directory

## Example Termux scripts

The app includes example scripts in `termux-scripts/`.
You can copy them to `~/bin/` in Termux or adapt them for your own setup.

### `espidf-monitor.sh`

```sh
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
```

### `espidf-flash.sh`

```sh
#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
PROJECT="${1:-$HOME/ESP32-RoboEyes}"
TTY="${TTY:-$TMPDIR/ttyesp32}"
PORT="${PORT:-6667}"
CTRL_PORT="${CTRL_PORT:-6668}"

printf 'BOOTLOADER\n' | nc 127.0.0.1 "$CTRL_PORT" || true
sleep 1

mkdir -p "$(dirname "$TTY")"
socat -d -d pty,raw,echo=0,link="$TTY" tcp:127.0.0.1:"$PORT" &
SOCAT_PID=$!
trap 'kill $SOCAT_PID >/dev/null 2>&1 || true' EXIT

cd "$PROJECT"
source ~/esp-idf/export.sh
idf.py -p "$TTY" -b 115200 flash
```

### `espidf-build.sh`

```sh
#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
PROJECT="${1:-$HOME/ESP32-RoboEyes}"

cd "$PROJECT"
source ~/esp-idf/export.sh
idf.py build
```

## How the bridge works

- The Android app owns the USB serial device
- The app exposes a raw TCP bridge on `127.0.0.1:6667`
- Termux tools connect to that TCP port
- `socat` creates a local pseudo-TTY for `idf.py`
- The control port `127.0.0.1:6668` accepts simple commands:
  - `DTR 1`
  - `DTR 0`
  - `RTS 1`
  - `RTS 0`
  - `RESET`
  - `BOOTLOADER`
  - `STATUS`

## Binary-safe behavior

This bridge is designed for **raw binary transport**.

That means:

- no text parsing of serial data
- no line buffering
- no terminal emulation assumptions
- suitable for ESP-IDF flashing and monitor workflows

## Bootloader and reset notes

Different ESP32 boards wire DTR and RTS differently.
For that reason, the app includes configurable options for:

- DTR inversion
- RTS inversion
- DTR/RTS swap
- bootloader timing
- reset pulse timing

If your board does not enter bootloader mode correctly on the first try, adjust those settings.

## Build in Android Studio

1. Open the project folder in Android Studio
2. Let Gradle sync
3. Build and run on a device with USB OTG support
4. Connect the ESP32 board and grant USB permission

## Recommended workflow

1. Open the app
2. Connect USB
3. Start the TCP server
4. Run the monitor script in Termux
5. For flashing, use the Bootloader button or the control port

## Notes

- The bridge is intended for Android devices that support USB OTG host mode.
- If intent-based Termux launch does not work on your device, use the generated scripts manually.
- For flashing reliability, keep the board wiring and DTR/RTS mapping aligned with your USB adapter / board design.

## License

Add a license file before publishing publicly if you want to distribute this project.
