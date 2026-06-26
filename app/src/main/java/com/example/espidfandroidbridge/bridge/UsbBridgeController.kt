package com.example.espidfandroidbridge.bridge

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

class UsbBridgeController(private val context: Context) {
    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private val prober = UsbSerialProber.getDefaultProber()
    private val rxBytes = AtomicLong(0)
    private val txBytes = AtomicLong(0)
    private val lock = Any()

    @Volatile private var currentPort: UsbSerialPort? = null
    @Volatile private var currentConnection: android.hardware.usb.UsbDeviceConnection? = null
    @Volatile private var currentDriver: UsbSerialDriver? = null
    @Volatile private var tcpServer: ServerSocket? = null
    @Volatile private var controlServer: ServerSocket? = null
    @Volatile private var tcpClient: Socket? = null
    @Volatile private var tcpOutput: OutputStream? = null
    @Volatile private var tcpInput: InputStream? = null
    @Volatile private var tcpJob: Job? = null
    @Volatile private var controlJob: Job? = null
    @Volatile private var usbReadJob: Job? = null
    @Volatile private var connecting = false
    @Volatile private var logicalDtr = false
    @Volatile private var logicalRts = false

    fun connectUsb() {
        if (currentPort != null) {
            BridgeStateStore.appendLog("USB already connected")
            return
        }
        if (connecting) return
        connecting = true
        try {
            val drivers = prober.findAllDrivers(usbManager)
            if (drivers.isEmpty()) {
                BridgeStateStore.setUsbStatus("No supported USB serial driver found")
                BridgeStateStore.appendLog("No supported driver found (CDC/CH340/CP210x/FTDI)")
                return
            }
            val driver = drivers.first()
            val device = driver.device
            BridgeStateStore.appendLog("USB driver detected: ${driver.javaClass.simpleName} for ${device.deviceName}")
            if (!usbManager.hasPermission(device)) {
                requestUsbPermission(device)
                BridgeStateStore.setUsbStatus("USB permission requested")
                return
            }
            openDriver(driver)
        } finally {
            connecting = false
        }
    }

    fun disconnectUsb() {
        usbReadJob?.cancel()
        usbReadJob = null
        try {
            currentPort?.close()
        } catch (_: Throwable) {
        }
        try {
            currentConnection?.close()
        } catch (_: Throwable) {
        }
        currentPort = null
        currentConnection = null
        currentDriver = null
        BridgeStateStore.setSerialConnected(false)
        BridgeStateStore.setUsbStatus("USB disconnected")
        BridgeStateStore.appendLog("USB disconnected")
    }

    fun startTcpServer() {
        if (tcpJob?.isActive == true) return
        val config = BridgeStateStore.state.value.config
        tcpJob = scope.launch { runTcpServer(config.tcpPort) }
        BridgeStateStore.setTcpRunning(true)
        BridgeStateStore.setTcpStatus("TCP server starting on 127.0.0.1:${config.tcpPort}")
        BridgeStateStore.appendLog("TCP server starting on 127.0.0.1:${config.tcpPort}")
        if (config.enableControlPort) startControlServer(config.controlPort)
    }

    fun stopTcpServer() {
        tcpJob?.cancel()
        controlJob?.cancel()
        closeTcpClient()
        try {
            tcpServer?.close()
        } catch (_: Throwable) {
        }
        try {
            controlServer?.close()
        } catch (_: Throwable) {
        }
        tcpServer = null
        controlServer = null
        tcpJob = null
        controlJob = null
        BridgeStateStore.setTcpRunning(false)
        BridgeStateStore.setControlRunning(false)
        BridgeStateStore.setTcpStatus("TCP server stopped")
        BridgeStateStore.appendLog("TCP server stopped")
    }

    fun setDTR(value: Boolean) = scope.launch {
        logicalDtr = value
        applyLineState()
    }

    fun setRTS(value: Boolean) = scope.launch {
        logicalRts = value
        applyLineState()
    }

    fun resetEsp32() = scope.launch {
        val cfg = BridgeStateStore.state.value.config
        BridgeStateStore.appendLog("ESP32 reset pulse")
        applyBootResetLines(bootActive = false, resetActive = true)
        delay(cfg.resetPulseMs)
        applyBootResetLines(bootActive = false, resetActive = false)
    }

    fun enterBootloader() = scope.launch {
        val cfg = BridgeStateStore.state.value.config
        BridgeStateStore.appendLog("ESP32 bootloader sequence")
        applyBootResetLines(bootActive = true, resetActive = true)
        delay(cfg.resetPulseMs)
        applyBootResetLines(bootActive = true, resetActive = false)
        delay(cfg.bootloaderTimingMs)
        applyBootResetLines(bootActive = false, resetActive = false)
    }

    fun updateConfig(config: BridgeConfig) {
        BridgeStateStore.setConfig(config)
        BridgePreferences.save(appContext, config)
        if (currentPort != null) {
            scope.launch { configurePort() }
        }
    }

    fun appendEspIdfOutput(text: String) {
        BridgeStateStore.appendEspIdfOutput(text)
    }

    fun sendRaw(bytes: ByteArray, length: Int) {
        val port = currentPort ?: return
        try {
            port.write(bytes, length, 1000)
            txBytes.addAndGet(length.toLong())
            BridgeStateStore.setCounters(rxBytes.get(), txBytes.get())
        } catch (e: IOException) {
            BridgeStateStore.appendLog("USB write failed: ${e.message}")
            disconnectUsb()
        }
    }

    fun shutdown() {
        stopTcpServer()
        disconnectUsb()
        scope.cancel()
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val permissionIntent = PendingIntent.getBroadcast(
            appContext,
            device.deviceId,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun openDriver(driver: UsbSerialDriver) {
        try {
            val connection = usbManager.openDevice(driver.device) ?: run {
                BridgeStateStore.setUsbStatus("Failed to open USB device")
                BridgeStateStore.appendLog("openDevice() returned null")
                return
            }
            val port = driver.ports.firstOrNull() ?: run {
                BridgeStateStore.setUsbStatus("No serial port exposed by driver")
                BridgeStateStore.appendLog("Driver has no ports")
                connection.close()
                return
            }
            port.open(connection)
            currentConnection = connection
            currentPort = port
            currentDriver = driver
            configurePort()
            startUsbReadLoop(port)
            BridgeStateStore.setSerialConnected(true)
            BridgeStateStore.setUsbStatus("USB connected")
            BridgeStateStore.setUsbInfo(driver.javaClass.simpleName, driver.device.deviceName)
            BridgeStateStore.appendLog("USB connected: ${driver.javaClass.simpleName} / ${driver.device.deviceName}")
        } catch (e: Exception) {
            BridgeStateStore.setUsbStatus("USB open failed: ${e.message}")
            BridgeStateStore.appendLog("USB open failed: ${e.message}")
            disconnectUsb()
        }
    }

    private fun configurePort() {
        val port = currentPort ?: return
        val cfg = BridgeStateStore.state.value.config
        try {
            port.setParameters(cfg.baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            applyMappedLines(port, cfg, bootActive = false, resetActive = false)
            BridgeStateStore.appendLog("Configured baud=${cfg.baudRate}")
        } catch (e: Exception) {
            BridgeStateStore.appendLog("Serial configure failed: ${e.message}")
        }
    }

    private fun startUsbReadLoop(port: UsbSerialPort) {
        usbReadJob?.cancel()
        usbReadJob = scope.launch {
            val buffer = ByteArray(4096)
            while (isActive && currentPort === port) {
                try {
                    val read = port.read(buffer, 250)
                    if (read > 0) {
                        synchronized(lock) {
                            tcpOutput?.let {
                                it.write(buffer, 0, read)
                                it.flush()
                            }
                        }
                        rxBytes.addAndGet(read.toLong())
                        BridgeStateStore.setCounters(rxBytes.get(), txBytes.get())
                    }
                } catch (e: IOException) {
                    BridgeStateStore.appendLog("USB read failed: ${e.message}")
                    disconnectUsb()
                    break
                }
            }
        }
    }

    private suspend fun runTcpServer(port: Int) {
        try {
            tcpServer = ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))
            while (scope.isActive && tcpJob?.isActive == true) {
                val socket = tcpServer?.accept() ?: break
                handleClient(socket)
            }
        } catch (e: IOException) {
            if (tcpJob?.isActive == true) {
                BridgeStateStore.appendLog("TCP server error: ${e.message}")
                BridgeStateStore.setTcpStatus("TCP server error: ${e.message}")
            }
        } finally {
            closeTcpClient()
            try {
                tcpServer?.close()
            } catch (_: Throwable) {
            }
            tcpServer = null
            BridgeStateStore.setTcpRunning(false)
            BridgeStateStore.setTcpStatus("TCP server stopped")
        }
    }

    private suspend fun handleClient(socket: Socket) {
        synchronized(lock) {
            closeTcpClient()
            tcpClient = socket
            tcpOutput = socket.getOutputStream()
            tcpInput = socket.getInputStream()
        }
        BridgeStateStore.appendLog("TCP client connected: ${socket.inetAddress.hostAddress}:${socket.port}")
        BridgeStateStore.setTcpStatus("TCP client connected")
        try {
            val input = socket.getInputStream()
            val buffer = ByteArray(4096)
            while (scope.isActive && !socket.isClosed) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) sendRaw(buffer, read)
            }
        } catch (e: IOException) {
            BridgeStateStore.appendLog("TCP client error: ${e.message}")
        } finally {
            BridgeStateStore.appendLog("TCP client disconnected")
            BridgeStateStore.setTcpStatus("TCP client disconnected")
            closeTcpClient()
        }
    }

    private fun closeTcpClient() {
        synchronized(lock) {
            try {
                tcpInput?.close()
            } catch (_: Throwable) {
            }
            try {
                tcpOutput?.close()
            } catch (_: Throwable) {
            }
            try {
                tcpClient?.close()
            } catch (_: Throwable) {
            }
            tcpInput = null
            tcpOutput = null
            tcpClient = null
        }
    }

    private fun startControlServer(port: Int) {
        if (controlJob?.isActive == true) return
        controlJob = scope.launch {
            try {
                controlServer = ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))
                BridgeStateStore.setControlRunning(true)
                BridgeStateStore.appendLog("Control server on 127.0.0.1:$port")
                while (scope.isActive && controlJob?.isActive == true) {
                    val socket = controlServer?.accept() ?: break
                    launch { handleControlClient(socket) }
                }
            } catch (e: IOException) {
                BridgeStateStore.appendLog("Control server error: ${e.message}")
            } finally {
                try {
                    controlServer?.close()
                } catch (_: Throwable) {
                }
                controlServer = null
                BridgeStateStore.setControlRunning(false)
            }
        }
    }

    private suspend fun handleControlClient(socket: Socket) {
        socket.use {
            val reader = it.getInputStream().bufferedReader()
            val writer = it.getOutputStream().bufferedWriter()
            writer.write("ESP-IDF Android Bridge ready\n")
            writer.flush()
            reader.lineSequence().forEach { line ->
                when (line.trim().uppercase()) {
                    "DTR 1" -> {
                        setDTR(true)
                        writer.write("OK\n")
                    }
                    "DTR 0" -> {
                        setDTR(false)
                        writer.write("OK\n")
                    }
                    "RTS 1" -> {
                        setRTS(true)
                        writer.write("OK\n")
                    }
                    "RTS 0" -> {
                        setRTS(false)
                        writer.write("OK\n")
                    }
                    "RESET" -> {
                        resetEsp32()
                        writer.write("OK\n")
                    }
                    "BOOTLOADER" -> {
                        enterBootloader()
                        writer.write("OK\n")
                    }
                    "STATUS" -> {
                        val s = BridgeStateStore.state.value
                        writer.write("RX=${s.rxBytes} TX=${s.txBytes} DTR=${s.dtr} RTS=${s.rts} USB=${s.usbStatus} TCP=${s.tcpStatus}\n")
                    }
                    else -> writer.write("ERR unknown command\n")
                }
                writer.flush()
            }
        }
    }

    private suspend fun applyLineState() {
        val port = currentPort ?: return
        val cfg = BridgeStateStore.state.value.config
        applyMappedLines(port, cfg, logicalDtr, logicalRts)
    }

    private suspend fun applyBootResetLines(bootActive: Boolean, resetActive: Boolean) {
        val port = currentPort ?: return
        val cfg = BridgeStateStore.state.value.config
        applyMappedLines(port, cfg, bootActive, resetActive)
    }

    private fun applyMappedLines(port: UsbSerialPort, cfg: BridgeConfig, bootActive: Boolean, resetActive: Boolean) {
        val logicalDtr = if (cfg.swapDtrRts) resetActive else bootActive
        val logicalRts = if (cfg.swapDtrRts) bootActive else resetActive
        val portDtr = logicalDtr xor cfg.invertDtr
        val portRts = logicalRts xor cfg.invertRts
        try {
            port.setDTR(portDtr)
            port.setRTS(portRts)
            BridgeStateStore.setLines(bootActive, resetActive)
        } catch (e: IOException) {
            BridgeStateStore.appendLog("Line control failed: ${e.message}")
        }
    }

    companion object {
        const val ACTION_USB_PERMISSION = "com.example.espidfandroidbridge.USB_PERMISSION"
    }
}
