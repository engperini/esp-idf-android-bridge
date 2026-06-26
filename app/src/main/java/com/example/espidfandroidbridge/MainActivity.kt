package com.example.espidfandroidbridge

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.espidfandroidbridge.bridge.BridgeConfig
import com.example.espidfandroidbridge.bridge.BridgePreferences
import com.example.espidfandroidbridge.bridge.BridgeService
import com.example.espidfandroidbridge.bridge.BridgeStateStore
import com.example.espidfandroidbridge.bridge.TermuxIntegration

class MainActivity : ComponentActivity() {
    private var permissionReceiverRegistered = false
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BridgeService.ACTION_USB_PERMISSION) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (granted) {
                    BridgeStateStore.appendLog("USB permission granted")
                    BridgeService.start(this@MainActivity, BridgeService.ACTION_CONNECT_USB)
                } else {
                    BridgeStateStore.appendLog("USB permission denied")
                    BridgeStateStore.setUsbStatus("USB permission denied")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BridgeStateStore.setConfig(BridgePreferences.load(this))
        setContent {
            MaterialTheme {
                val state by BridgeStateStore.state.collectAsStateWithLifecycle()
                App(state = state)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!permissionReceiverRegistered) {
            val filter = IntentFilter(BridgeService.ACTION_USB_PERMISSION)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(permissionReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(permissionReceiver, filter)
            }
            permissionReceiverRegistered = true
        }
    }

    override fun onPause() {
        if (permissionReceiverRegistered) {
            unregisterReceiver(permissionReceiver)
            permissionReceiverRegistered = false
        }
        super.onPause()
    }
}

@Composable
private fun App(state: com.example.espidfandroidbridge.bridge.BridgeUiState) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val clipboard = LocalClipboardManager.current
    var config by remember(state.config) { mutableStateOf(state.config) }
    var projectPath by remember(state.config.projectPath) { mutableStateOf(state.config.projectPath) }
    val tabs = listOf("Main", "ESP-IDF", "Settings")

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = state.selectedTab == index,
                        onClick = { BridgeStateStore.setSelectedTab(index) },
                        icon = { Icon(Icons.Default.Usb, contentDescription = null) },
                        label = { Text(title) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            when (state.selectedTab) {
                0 -> MainScreen(
                    state = state,
                    onConnectUsb = { requestUsbPermission(context) },
                    onStartTcp = { BridgeService.start(context, BridgeService.ACTION_START_TCP) },
                    onStopTcp = { BridgeService.start(context, BridgeService.ACTION_STOP_TCP) },
                    onBootloader = { BridgeService.start(context, BridgeService.ACTION_BOOTLOADER) },
                    onReset = { BridgeService.start(context, BridgeService.ACTION_RESET) },
                    onToggleDtr = { BridgeService.start(context, BridgeService.ACTION_SET_DTR, it) },
                    onToggleRts = { BridgeService.start(context, BridgeService.ACTION_SET_RTS, it) },
                    onCopyMonitor = {
                        val command = TermuxIntegration.buildMonitorScript(state.config)
                        clipboard.setText(AnnotatedString(command))
                        BridgeStateStore.setTermuxMessage("Monitor script copied")
                    }
                )
                1 -> EspIdfScreen(
                    state = state,
                    projectPath = projectPath,
                    onProjectPathChange = { projectPath = it },
                    onBuild = {
                        val updated = state.config.copy(projectPath = projectPath)
                        BridgePreferences.save(context, updated)
                        BridgeStateStore.setConfig(updated)
                        runTermux(context, clipboard, updated, mode = "build")
                    },
                    onMonitor = {
                        val updated = state.config.copy(projectPath = projectPath)
                        BridgePreferences.save(context, updated)
                        BridgeStateStore.setConfig(updated)
                        runTermux(context, clipboard, updated, mode = "monitor")
                    },
                    onFlash = {
                        val updated = state.config.copy(projectPath = projectPath)
                        BridgePreferences.save(context, updated)
                        BridgeStateStore.setConfig(updated)
                        runTermux(context, clipboard, updated, mode = "flash")
                    },
                    onCopy = {
                        val updated = state.config.copy(projectPath = projectPath)
                        val text = TermuxIntegration.buildFlashScript(updated)
                        clipboard.setText(AnnotatedString(text))
                        BridgeStateStore.setTermuxMessage("Flash script copied")
                    }
                )
                2 -> SettingsScreen(
                    config = config,
                    driverInfo = state.usbDriverInfo,
                    deviceInfo = state.usbDeviceInfo,
                    onChange = { config = it },
                    onSave = {
                        BridgePreferences.save(context, config)
                        BridgeStateStore.setConfig(config)
                        BridgeStateStore.appendLog("Settings saved")
                        if (config.keepScreenOn) {
                            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                        BridgeService.start(context, BridgeService.ACTION_UPDATE_CONFIG)
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            if (state.termuxMessage.isNotBlank()) {
                Card(colors = CardDefaults.cardColors()) {
                    Text(state.termuxMessage, modifier = Modifier.padding(12.dp))
                }
                Spacer(Modifier.height(8.dp))
            }

            if (state.logLines.isNotEmpty()) {
                Text("Live logs", style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.height(220.dp).fillMaxWidth()) {
                    items(state.logLines) { line ->
                        Text("${line.timestamp}: ${line.text}", fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScreen(
    state: com.example.espidfandroidbridge.bridge.BridgeUiState,
    onConnectUsb: () -> Unit,
    onStartTcp: () -> Unit,
    onStopTcp: () -> Unit,
    onBootloader: () -> Unit,
    onReset: () -> Unit,
    onToggleDtr: (Boolean) -> Unit,
    onToggleRts: (Boolean) -> Unit,
    onCopyMonitor: () -> Unit,
) {
    Text("ESP-IDF Android Bridge", style = MaterialTheme.typography.headlineSmall)
    Text(state.usbStatus)
    Text(state.tcpStatus)
    Text("Driver: ${state.usbDriverInfo}")
    Text("Device: ${state.usbDeviceInfo}")
    Text("RX ${state.rxBytes} bytes • TX ${state.txBytes} bytes")
    Text("DTR: ${state.dtr} • RTS: ${state.rts}")
    Text("TCP 127.0.0.1:${state.config.tcpPort} • CTRL 127.0.0.1:${state.config.controlPort}")
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onConnectUsb) { Text("Connect USB") }
        Button(onClick = onStartTcp) { Text("Start Server") }
        OutlinedButton(onClick = onStopTcp) { Text("Stop Server") }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onBootloader) { Text("Enter Bootloader") }
        Button(onClick = onReset) { Text("Reset ESP32") }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onCopyMonitor) { Text("Copy Monitor Script") }
        OutlinedButton(onClick = { onToggleDtr(!state.dtr) }) { Text(if (state.dtr) "DTR 1" else "DTR 0") }
        OutlinedButton(onClick = { onToggleRts(!state.rts) }) { Text(if (state.rts) "RTS 1" else "RTS 0") }
    }
    Spacer(Modifier.height(12.dp))
    Card(colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(12.dp)) {
            Text("Binary-safe bridge", style = MaterialTheme.typography.titleMedium)
            Text("TCP 127.0.0.1:6667 ↔ USB serial with DTR/RTS control")
        }
    }
}

@Composable
private fun EspIdfScreen(
    state: com.example.espidfandroidbridge.bridge.BridgeUiState,
    projectPath: String,
    onProjectPathChange: (String) -> Unit,
    onBuild: () -> Unit,
    onMonitor: () -> Unit,
    onFlash: () -> Unit,
    onCopy: () -> Unit,
) {
    Text("ESP-IDF integration", style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
        value = projectPath,
        onValueChange = onProjectPathChange,
        label = { Text("Project path inside Termux/Ubuntu") },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onBuild) { Text("Build") }
        Button(onClick = onMonitor) { Text("Monitor") }
        Button(onClick = onFlash) { Text("Flash") }
        OutlinedButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Copy")
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("Tip: if Termux intents are blocked, copy the script and run it manually in Termux.")
    Spacer(Modifier.height(8.dp))
    Text("Preview:", style = MaterialTheme.typography.titleMedium)
    Text(
        TermuxIntegration.buildMonitorScript(state.config.copy(projectPath = projectPath)),
        fontFamily = FontFamily.Monospace
    )
    Spacer(Modifier.height(8.dp))
    Text("ESP-IDF output", style = MaterialTheme.typography.titleMedium)
    LazyColumn(Modifier.height(180.dp).fillMaxWidth()) {
        items(state.espIdfOutput) { line ->
            Text(line, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun SettingsScreen(
    config: BridgeConfig,
    driverInfo: String,
    deviceInfo: String,
    onChange: (BridgeConfig) -> Unit,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text("Selected USB driver info: $driverInfo")
        Text("USB device: $deviceInfo")
        IntField("TCP port", config.tcpPort) { onChange(config.copy(tcpPort = it)) }
        IntField("Control port", config.controlPort) { onChange(config.copy(controlPort = it)) }
        IntField("Baud rate", config.baudRate) { onChange(config.copy(baudRate = it)) }
        LongField("Bootloader timing ms", config.bootloaderTimingMs) { onChange(config.copy(bootloaderTimingMs = it)) }
        LongField("Reset pulse ms", config.resetPulseMs) { onChange(config.copy(resetPulseMs = it)) }
        ToggleRow("Invert DTR", config.invertDtr) { onChange(config.copy(invertDtr = it)) }
        ToggleRow("Invert RTS", config.invertRts) { onChange(config.copy(invertRts = it)) }
        ToggleRow("Swap DTR/RTS", config.swapDtrRts) { onChange(config.copy(swapDtrRts = it)) }
        ToggleRow("Enable control port", config.enableControlPort) { onChange(config.copy(enableControlPort = it)) }
        ToggleRow("Keep screen on", config.keepScreenOn) { onChange(config.copy(keepScreenOn = it)) }
        OutlinedTextField(
            value = config.ttyPath,
            onValueChange = { onChange(config.copy(ttyPath = it)) },
            label = { Text("TTY path for scripts") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = onSave) { Text("Save settings") }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onValueChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = value, onCheckedChange = onValueChange)
    }
}

@Composable
private fun IntField(label: String, value: Int, onValueChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { new -> text = new; new.toIntOrNull()?.let(onValueChange) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun LongField(label: String, value: Long, onValueChange: (Long) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { new -> text = new; new.toLongOrNull()?.let(onValueChange) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

private fun requestUsbPermission(context: Context) {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val device = usbManager.deviceList.values.firstOrNull() ?: run {
        BridgeStateStore.appendLog("No USB device attached")
        return
    }
    if (usbManager.hasPermission(device)) {
        BridgeStateStore.appendLog("USB permission already granted")
        BridgeService.start(context, BridgeService.ACTION_CONNECT_USB)
        return
    }
    val permissionIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(BridgeService.ACTION_USB_PERMISSION),
        PendingIntent.FLAG_IMMUTABLE
    )
    usbManager.requestPermission(device, permissionIntent)
    BridgeStateStore.appendLog("USB permission request sent")
}

private fun runTermux(context: Context, clipboard: androidx.compose.ui.platform.ClipboardManager, config: BridgeConfig, mode: String) {
    val command = when (mode) {
        "flash" -> TermuxIntegration.buildFlashScript(config)
        "monitor" -> TermuxIntegration.buildMonitorScript(config)
        else -> TermuxIntegration.buildBuildScript(config)
    }
    clipboard.setText(AnnotatedString(command))
    BridgeStateStore.appendEspIdfOutput("[$mode] $command")
    val launched = TermuxIntegration.tryLaunchTermuxCommand(context, command)
    BridgeStateStore.setTermuxMessage(if (launched) "Termux launch attempted" else "Command copied to clipboard")
}
