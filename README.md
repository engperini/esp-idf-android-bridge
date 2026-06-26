# ESP-IDF Android Bridge

    Android Studio Kotlin app that bridges raw binary data between **USB serial** and **localhost TCP** for ESP32 development on Android.

    ## What it does

    - Connects to ESP32 / ESP32-S3 over USB OTG
    - Uses `usb-serial-for-android` for CDC ACM, CH340, CP210x, and FTDI devices
    - Runs a binary-safe TCP server on `127.0.0.1:6667`
    - Supports DTR / RTS control
    - Includes a control port on `127.0.0.1:6668` for boot/reset actions
    - Provides ESP-IDF helper commands for Termux + Ubuntu proot

    ## Important limitation

    Direct execution of Termux commands from a normal Android app is not guaranteed because of Android sandboxing and Termux plugin differences.

    This project therefore does **both**:

    1. Tries a best-effort intent launch for Termux / Termux:Tasker style integrations
    2. Always provides copyable shell scripts you can run manually in Termux

    ## Project structure

    - `app/src/main/java/com/example/espidfandroidbridge/bridge/BridgeService.kt`
    - `app/src/main/java/com/example/espidfandroidbridge/bridge/UsbBridgeController.kt`
    - `app/src/main/java/com/example/espidfandroidbridge/bridge/TermuxIntegration.kt`
    - `app/src/main/java/com/example/espidfandroidbridge/MainActivity.kt`

    ## Termux setup

    Install these in Termux:

    ```sh
    pkg update
    pkg install socat netcat-openbsd
    ```

    Make sure Ubuntu proot and ESP-IDF are already available, as in your current setup.

    ## Example scripts

    Put these in `~/bin/` inside Termux and make them executable.

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

    printf 'BOOTLOADER
' | nc 127.0.0.1 "$CTRL_PORT" || true
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

    - The app owns the USB serial device
    - Termux connects to `127.0.0.1:6667`
    - `socat` creates a local pseudo-TTY for `idf.py`
    - Port `6668` receives simple control commands:
      - `DTR 1`
      - `DTR 0`
      - `RTS 1`
      - `RTS 0`
      - `RESET`
      - `BOOTLOADER`

    ## Notes

    - The bridge is binary-safe; it does not treat traffic as text.
    - You may need to tune `Invert DTR`, `Invert RTS`, and `Swap DTR/RTS` per board wiring.
    - Flashing works best when the board is already in bootloader mode.
    - If the intent-based Termux launch does not work, copy the generated script and run it manually.

    ## Build in Android Studio

    1. Open the project folder in Android Studio
    2. Let Gradle sync
    3. Run on a phone with USB OTG support
    4. Connect the ESP32 board and grant USB permission

    ## Suggested workflow

    1. Start the app
    2. Connect USB
    3. Start TCP server
    4. Run the Termux monitor script
    5. For flashing, use the bootloader button or the control port
