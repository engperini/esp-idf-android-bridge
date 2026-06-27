package com.example.espidfandroidbridge.bridge

const val DEFAULT_PROJECT_PATH = "/root/ESP32-RoboEyes"
const val DEFAULT_FLASH_ARGS = "--before no_reset --after no_reset write_flash @flash_args"

data class BridgeConfig(
    val tcpPort: Int = 6667,
    val controlPort: Int = 6668,
    val baudRate: Int = 115200,
    val invertDtr: Boolean = false,
    val invertRts: Boolean = false,
    val swapDtrRts: Boolean = true,
    val bootloaderTimingMs: Long = 250,
    val resetPulseMs: Long = 120,
    val keepScreenOn: Boolean = false,
    val enableControlPort: Boolean = true,
    val projectPath: String = DEFAULT_PROJECT_PATH,
    val flashArgs: String = DEFAULT_FLASH_ARGS,
    val ttyPath: String = "/tmp/ttyesp32"
)

data class BridgeLogLine(
    val timestamp: Long,
    val text: String
)

data class BridgeUiState(
    val config: BridgeConfig = BridgeConfig(),
    val usbStatus: String = "USB disconnected",
    val tcpStatus: String = "TCP server stopped",
    val tcpServerStatus: String = "TCP server stopped",
    val controlStatus: String = "Control server stopped",
    val bootloaderStatus: String = "Bootloader idle",
    val usbDriverInfo: String = "No device selected",
    val usbDeviceInfo: String = "No USB device",
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val dtr: Boolean = false,
    val rts: Boolean = false,
    val serialConnected: Boolean = false,
    val tcpRunning: Boolean = false,
    val controlRunning: Boolean = false,
    val logLines: List<BridgeLogLine> = emptyList(),
    val espIdfOutput: List<String> = emptyList(),
    val selectedTab: Int = 0,
    val termuxMessage: String = ""
)
