package com.example.espidfandroidbridge.bridge

data class BridgeConfig(
    val tcpPort: Int = 6667,
    val controlPort: Int = 6668,
    val baudRate: Int = 115200,
    val invertDtr: Boolean = false,
    val invertRts: Boolean = false,
    val swapDtrRts: Boolean = false,
    val bootloaderTimingMs: Long = 250,
    val resetPulseMs: Long = 120,
    val keepScreenOn: Boolean = false,
    val enableControlPort: Boolean = true,
    val projectPath: String = "",
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
