package com.example.espidfandroidbridge.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

object TermuxIntegration {
    fun buildMonitorScript(config: BridgeConfig): String = buildString {
        val project = config.projectPath.ifBlank { "${'$'}HOME/ESP32-RoboEyes" }
        appendLine("#!/data/data/com.termux/files/usr/bin/bash")
        appendLine("set -euo pipefail")
        appendLine("PROJECT=${shellQuote(project)}")
        appendLine("TTY=${shellQuote(config.ttyPath)}")
        appendLine("PORT=${config.tcpPort}")
        appendLine("BAUD=${config.baudRate}")
        appendLine("mkdir -p \"${'$'}(dirname \"${'$'}TTY\")\"")
        appendLine("socat -d -d pty,raw,echo=0,link=\"${'$'}TTY\" tcp:127.0.0.1:\"${'$'}PORT\" &")
        appendLine("SOCAT_PID=${'$'}!")
        appendLine("trap 'kill ${'$'}SOCAT_PID >/dev/null 2>&1 || true' EXIT")
        appendLine("cd \"${'$'}PROJECT\"")
        appendLine("source ~/esp-idf/export.sh")
        appendLine("idf.py -p \"${'$'}TTY\" -b \"${'$'}BAUD\" monitor")
    }

    fun buildFlashScript(config: BridgeConfig): String = buildString {
        val project = config.projectPath.ifBlank { "${'$'}HOME/ESP32-RoboEyes" }
        appendLine("#!/data/data/com.termux/files/usr/bin/bash")
        appendLine("set -euo pipefail")
        appendLine("PROJECT=${shellQuote(project)}")
        appendLine("TTY=${shellQuote(config.ttyPath)}")
        appendLine("PORT=${config.tcpPort}")
        appendLine("CTRL_PORT=${config.controlPort}")
        appendLine("BAUD=${config.baudRate}")
        appendLine("printf 'BOOTLOADER\\n' | nc 127.0.0.1 \"${'$'}CTRL_PORT\" || true")
        appendLine("sleep 1")
        appendLine("mkdir -p \"${'$'}(dirname \"${'$'}TTY\")\"")
        appendLine("socat -d -d pty,raw,echo=0,link=\"${'$'}TTY\" tcp:127.0.0.1:\"${'$'}PORT\" &")
        appendLine("SOCAT_PID=${'$'}!")
        appendLine("trap 'kill ${'$'}SOCAT_PID >/dev/null 2>&1 || true' EXIT")
        appendLine("cd \"${'$'}PROJECT\"")
        appendLine("source ~/esp-idf/export.sh")
        appendLine("idf.py -p \"${'$'}TTY\" -b \"${'$'}BAUD\" flash")
    }

    fun buildBuildScript(config: BridgeConfig): String = buildString {
        val project = config.projectPath.ifBlank { "${'$'}HOME/ESP32-RoboEyes" }
        appendLine("#!/data/data/com.termux/files/usr/bin/bash")
        appendLine("set -euo pipefail")
        appendLine("PROJECT=${shellQuote(project)}")
        appendLine("cd \"${'$'}PROJECT\"")
        appendLine("source ~/esp-idf/export.sh")
        appendLine("idf.py build")
    }

    fun tryLaunchTermuxCommand(context: Context, command: String): Boolean {
        val candidates = listOf(
            Intent("com.termux.RUN_COMMAND").setPackage("com.termux"),
            Intent("com.termux.tasker.RUN_COMMAND").setPackage("com.termux.tasker"),
            Intent(Intent.ACTION_SEND).setPackage("com.termux")
        )
        for (intent in candidates) {
            val prepared = intent.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (action == Intent.ACTION_SEND) type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, command)
                putExtra("com.termux.app.extra.COMMAND", command)
            }
            if (prepared.resolveActivity(context.packageManager) != null) {
                context.startActivity(prepared)
                return true
            }
        }
        copyToClipboard(context, command)
        return false
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
