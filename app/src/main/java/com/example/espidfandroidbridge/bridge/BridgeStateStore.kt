package com.example.espidfandroidbridge.bridge

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

object BridgeStateStore {
    private val _state = MutableStateFlow(BridgeUiState())
    val state: StateFlow<BridgeUiState> = _state

    fun setConfig(config: BridgeConfig) = mutate { it.copy(config = config) }
    fun setSelectedTab(index: Int) = mutate { it.copy(selectedTab = index) }
    fun setUsbStatus(text: String) = mutate { it.copy(usbStatus = text) }
    fun setTcpStatus(text: String) = mutate { it.copy(tcpStatus = text) }
    fun setTcpServerStatus(text: String) = mutate { it.copy(tcpServerStatus = text) }
    fun setControlStatus(text: String) = mutate { it.copy(controlStatus = text) }
    fun setBootloaderStatus(text: String) = mutate { it.copy(bootloaderStatus = text) }
    fun setUsbInfo(driver: String, device: String) = mutate { it.copy(usbDriverInfo = driver, usbDeviceInfo = device) }
    fun setSerialConnected(connected: Boolean) = mutate { it.copy(serialConnected = connected) }
    fun setTcpRunning(running: Boolean) = mutate { it.copy(tcpRunning = running) }
    fun setControlRunning(running: Boolean) = mutate { it.copy(controlRunning = running) }
    fun setCounters(rx: Long, tx: Long) = mutate { it.copy(rxBytes = rx, txBytes = tx) }
    fun setLines(dtr: Boolean, rts: Boolean) = mutate { it.copy(dtr = dtr, rts = rts) }
    fun setTermuxMessage(message: String) = mutate { it.copy(termuxMessage = message) }

    fun appendLog(text: String) = mutate {
        val line = BridgeLogLine(System.currentTimeMillis(), text)
        val logs = (it.logLines + line).takeLast(200)
        it.copy(logLines = logs)
    }

    fun appendEspIdfOutput(text: String) = mutate {
        val lines = (it.espIdfOutput + text).takeLast(500)
        it.copy(espIdfOutput = lines)
    }

    fun clearEspIdfOutput() = mutate { it.copy(espIdfOutput = emptyList()) }
    private fun mutate(transform: (BridgeUiState) -> BridgeUiState) = _state.update(transform)
}
