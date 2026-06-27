package com.example.espidfandroidbridge.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat

object TermuxIntegration {
    fun buildMonitorScript(config: BridgeConfig): String = buildString {
        val project = config.projectPath.ifBlank { DEFAULT_PROJECT_PATH }
        appendLine("#!/data/data/com.termux/files/usr/bin/bash")
        appendLine("set -euo pipefail")
        appendLine("PROJECT=${shellQuote(project)}")
        appendLine("TTY=${shellQuote(config.ttyPath.ifBlank { "/tmp/ttyesp32" })}")
        appendLine("PORT=${config.tcpPort}")
        appendLine("BAUD=${config.baudRate}")
        appendLine("proot-distro login ubuntu -- env PROJECT=\"${'$'}PROJECT\" TTY=\"${'$'}TTY\" PORT=\"${'$'}PORT\" BAUD=\"${'$'}BAUD\" bash -lc 'cd /root/esp-idf && . ./export.sh; mkdir -p \"${'$'}(dirname \"${'$'}TTY\")\"; socat -d -d pty,raw,echo=0,link=\"${'$'}TTY\" tcp:127.0.0.1:\"${'$'}PORT\" >/tmp/ttyesp32.log 2>&1 & SOCAT_PID=${'$'}!; trap \"kill ${'$'}SOCAT_PID >/dev/null 2>&1 || true\" EXIT; cd \"${'$'}PROJECT\"; idf.py -p \"${'$'}TTY\" -b \"${'$'}BAUD\" monitor'")
    }

    fun buildFlashScript(config: BridgeConfig): String = buildString {
        val project = config.projectPath.ifBlank { DEFAULT_PROJECT_PATH }
        appendLine("#!/data/data/com.termux/files/usr/bin/bash")
        appendLine("set -euo pipefail")
        appendLine("PROJECT=${shellQuote(project)}")
        appendLine("TTY=${shellQuote(config.ttyPath.ifBlank { "/tmp/ttyesp32" })}")
        appendLine("PORT=${config.tcpPort}")
        appendLine("CTRL_PORT=${config.controlPort}")
        appendLine("BAUD=${config.baudRate}")
        appendLine("FLASH_ARGS=${shellQuote(config.flashArgs.ifBlank { DEFAULT_FLASH_ARGS })}")
        appendLine("ctrl_cmd() {")
        appendLine("  local cmd=\"${'$'}1\"")
        appendLine("  local response=\"\"")
        appendLine("  if ! exec 3<>\"/dev/tcp/127.0.0.1/${'$'}CTRL_PORT\"; then")
        appendLine("    echo '[bridge] control port unavailable'")
        appendLine("    return 1")
        appendLine("  fi")
        appendLine("  IFS= read -r -t 5 _ <&3 || true")
        appendLine("  printf '%s\\n' \"${'$'}cmd\" >&3")
        appendLine("  IFS= read -r -t 5 response <&3 || true")
        appendLine("  exec 3<&- 3>&-")
        appendLine("  printf '%s\\n' \"${'$'}response\"")
        appendLine("}")
        appendLine("echo '[bridge] control preflight: STATUS'")
        appendLine("status_before=${'$'}(ctrl_cmd STATUS)")
        appendLine("echo \"[bridge] ${'$'}status_before\"")
        appendLine("echo '[bridge] control preflight: BOOTLOADER'")
        appendLine("boot_ack=${'$'}(ctrl_cmd BOOTLOADER)")
        appendLine("echo \"[bridge] ${'$'}boot_ack\"")
        appendLine("sleep 1")
        appendLine("echo '[bridge] control post-check: STATUS'")
        appendLine("status_after=${'$'}(ctrl_cmd STATUS)")
        appendLine("echo \"[bridge] ${'$'}status_after\"")
        appendLine("proot-distro login ubuntu -- env PROJECT=\"${'$'}PROJECT\" TTY=\"${'$'}TTY\" PORT=\"${'$'}PORT\" FLASH_ARGS=\"${'$'}FLASH_ARGS\" BAUD=\"${'$'}BAUD\" bash -lc 'cd /root/esp-idf && . ./export.sh; mkdir -p \"${'$'}(dirname \"${'$'}TTY\")\"; socat -d -d pty,raw,echo=0,link=\"${'$'}TTY\" tcp:127.0.0.1:\"${'$'}PORT\" >/tmp/ttyesp32.log 2>&1 & SOCAT_PID=${'$'}!; trap \"kill ${'$'}SOCAT_PID >/dev/null 2>&1 || true\" EXIT; cd \"${'$'}PROJECT/build\"; python -m esptool --chip esp32s3 -p \"${'$'}TTY\" -b \"${'$'}BAUD\" ${'$'}FLASH_ARGS'")
    }

    fun buildBuildScript(config: BridgeConfig): String = buildString {
        val project = config.projectPath.ifBlank { DEFAULT_PROJECT_PATH }
        appendLine("#!/data/data/com.termux/files/usr/bin/bash")
        appendLine("set -euo pipefail")
        appendLine("PROJECT=${shellQuote(project)}")
        appendLine("proot-distro login ubuntu -- env PROJECT=\"${'$'}PROJECT\" bash -lc 'cd /root/esp-idf && . ./export.sh && cd \"${'$'}PROJECT\" && idf.py build'")
    }

    fun buildBootloaderProbeScript(config: BridgeConfig): String = buildReadChipIdScript(config)

    fun buildReadChipIdScript(config: BridgeConfig): String = buildString {
        appendLine("#!/data/data/com.termux/files/usr/bin/bash")
        appendLine("set -euo pipefail")
        appendLine("TTY=${shellQuote(config.ttyPath.ifBlank { "/tmp/ttyesp32" })}")
        appendLine("PORT=${config.tcpPort}")
        appendLine("BAUD=${config.baudRate}")
        appendLine("proot-distro login ubuntu -- env TTY=\"${'$'}TTY\" PORT=\"${'$'}PORT\" BAUD=\"${'$'}BAUD\" bash -lc 'cd /root/esp-idf && . ./export.sh; mkdir -p \"${'$'}(dirname \"${'$'}TTY\")\"; socat -d -d pty,raw,echo=0,link=\"${'$'}TTY\" tcp:127.0.0.1:\"${'$'}PORT\" >/tmp/ttyesp32.log 2>&1 & SOCAT_PID=${'$'}!; trap \"kill ${'$'}SOCAT_PID >/dev/null 2>&1 || true\" EXIT; python -m esptool --chip esp32s3 -p \"${'$'}TTY\" -b \"${'$'}BAUD\" --before no_reset --after no_reset chip_id'")
    }

    fun buildFlashIdScript(config: BridgeConfig): String = buildString {
        appendLine("#!/data/data/com.termux/files/usr/bin/bash")
        appendLine("set -euo pipefail")
        appendLine("TTY=${shellQuote(config.ttyPath.ifBlank { "/tmp/ttyesp32" })}")
        appendLine("PORT=${config.tcpPort}")
        appendLine("BAUD=${config.baudRate}")
        appendLine("proot-distro login ubuntu -- env TTY=\"${'$'}TTY\" PORT=\"${'$'}PORT\" BAUD=\"${'$'}BAUD\" bash -lc 'cd /root/esp-idf && . ./export.sh; mkdir -p \"${'$'}(dirname \"${'$'}TTY\")\"; socat -d -d pty,raw,echo=0,link=\"${'$'}TTY\" tcp:127.0.0.1:\"${'$'}PORT\" >/tmp/ttyesp32.log 2>&1 & SOCAT_PID=${'$'}!; trap \"kill ${'$'}SOCAT_PID >/dev/null 2>&1 || true\" EXIT; python -m esptool --chip esp32s3 -p \"${'$'}TTY\" -b \"${'$'}BAUD\" --before no_reset --after no_reset flash_id'")
    }

    fun tryLaunchTermuxCommand(context: Context, command: String): Boolean {
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_STDIN", command)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "ESP-IDF Android Bridge")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            ContextCompat.startForegroundService(context, intent)
            true
        }.getOrElse {
            copyToClipboard(context, command)
            false
        }
    }

    fun copyToClipboard(context: Context, command: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ESP-IDF command", command))
    }

    fun installTermuxDeepLink(context: Context): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/"))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
