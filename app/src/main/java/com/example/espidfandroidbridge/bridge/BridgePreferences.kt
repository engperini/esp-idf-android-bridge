package com.example.espidfandroidbridge.bridge

import android.content.Context

object BridgePreferences {
    private const val FILE = "bridge_prefs"
    private const val K_TCP_PORT = "tcp_port"
    private const val K_CONTROL_PORT = "control_port"
    private const val K_BAUD = "baud_rate"
    private const val K_INVERT_DTR = "invert_dtr"
    private const val K_INVERT_RTS = "invert_rts"
    private const val K_SWAP = "swap_dtr_rts"
    private const val K_BOOT_MS = "bootloader_timing_ms"
    private const val K_RESET_MS = "reset_pulse_ms"
    private const val K_KEEP_ON = "keep_screen_on"
    private const val K_ENABLE_CONTROL = "enable_control_port"
    private const val K_PROJECT = "project_path"
    private const val K_TTY = "tty_path"

    fun load(context: Context): BridgeConfig {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return BridgeConfig(
            tcpPort = prefs.getInt(K_TCP_PORT, 6667),
            controlPort = prefs.getInt(K_CONTROL_PORT, 6668),
            baudRate = prefs.getInt(K_BAUD, 115200),
            invertDtr = prefs.getBoolean(K_INVERT_DTR, false),
            invertRts = prefs.getBoolean(K_INVERT_RTS, false),
            swapDtrRts = prefs.getBoolean(K_SWAP, false),
            bootloaderTimingMs = prefs.getLong(K_BOOT_MS, 250L),
            resetPulseMs = prefs.getLong(K_RESET_MS, 120L),
            keepScreenOn = prefs.getBoolean(K_KEEP_ON, false),
            enableControlPort = prefs.getBoolean(K_ENABLE_CONTROL, true),
            projectPath = prefs.getString(K_PROJECT, "") ?: "",
            ttyPath = prefs.getString(K_TTY, "/tmp/ttyesp32") ?: "/tmp/ttyesp32"
        )
    }

    fun save(context: Context, config: BridgeConfig) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt(K_TCP_PORT, config.tcpPort)
            .putInt(K_CONTROL_PORT, config.controlPort)
            .putInt(K_BAUD, config.baudRate)
            .putBoolean(K_INVERT_DTR, config.invertDtr)
            .putBoolean(K_INVERT_RTS, config.invertRts)
            .putBoolean(K_SWAP, config.swapDtrRts)
            .putLong(K_BOOT_MS, config.bootloaderTimingMs)
            .putLong(K_RESET_MS, config.resetPulseMs)
            .putBoolean(K_KEEP_ON, config.keepScreenOn)
            .putBoolean(K_ENABLE_CONTROL, config.enableControlPort)
            .putString(K_PROJECT, config.projectPath)
            .putString(K_TTY, config.ttyPath)
            .apply()
    }
}
