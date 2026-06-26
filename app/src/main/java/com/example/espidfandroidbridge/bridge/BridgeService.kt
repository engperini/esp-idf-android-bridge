package com.example.espidfandroidbridge.bridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class BridgeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: UsbBridgeController

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    BridgeStateStore.appendLog("USB device detached")
                    controller.disconnectUsb()
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    BridgeStateStore.appendLog("USB device attached")
                }
                UsbBridgeController.ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted) {
                        BridgeStateStore.appendLog("USB permission granted")
                        controller.connectUsb()
                    } else {
                        BridgeStateStore.appendLog("USB permission denied")
                        BridgeStateStore.setUsbStatus("USB permission denied")
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        controller = UsbBridgeController(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Bridge idle"))
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbBridgeController.ACTION_USB_PERMISSION)
        }
        registerReceiver(usbReceiver, filter)
        BridgePreferences.load(this).also {
            BridgeStateStore.setConfig(it)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT_USB -> controller.connectUsb()
            ACTION_DISCONNECT_USB -> controller.disconnectUsb()
            ACTION_START_TCP -> controller.startTcpServer()
            ACTION_STOP_TCP -> controller.stopTcpServer()
            ACTION_RESET -> controller.resetEsp32()
            ACTION_BOOTLOADER -> controller.enterBootloader()
            ACTION_SET_DTR -> controller.setDTR(intent.getBooleanExtra(EXTRA_BOOL, false))
            ACTION_SET_RTS -> controller.setRTS(intent.getBooleanExtra(EXTRA_BOOL, false))
            ACTION_UPDATE_CONFIG -> {
                val config = BridgePreferences.load(this)
                controller.updateConfig(config)
            }
        }
        val config = BridgeStateStore.state.value.config
        val title = if (BridgeStateStore.state.value.tcpRunning) "ESP-IDF Android Bridge running" else "ESP-IDF Android Bridge"
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(title, config))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(usbReceiver)
        controller.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ESP-IDF Android Bridge",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, config: BridgeConfig = BridgeStateStore.state.value.config) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(text)
            .setContentText("USB/TCP bridge on 127.0.0.1:${config.tcpPort}")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val CHANNEL_ID = "espidf_bridge"
        private const val NOTIFICATION_ID = 6667
        const val ACTION_USB_PERMISSION = UsbBridgeController.ACTION_USB_PERMISSION
        const val ACTION_CONNECT_USB = "com.example.espidfandroidbridge.action.CONNECT_USB"
        const val ACTION_DISCONNECT_USB = "com.example.espidfandroidbridge.action.DISCONNECT_USB"
        const val ACTION_START_TCP = "com.example.espidfandroidbridge.action.START_TCP"
        const val ACTION_STOP_TCP = "com.example.espidfandroidbridge.action.STOP_TCP"
        const val ACTION_RESET = "com.example.espidfandroidbridge.action.RESET"
        const val ACTION_BOOTLOADER = "com.example.espidfandroidbridge.action.BOOTLOADER"
        const val ACTION_SET_DTR = "com.example.espidfandroidbridge.action.SET_DTR"
        const val ACTION_SET_RTS = "com.example.espidfandroidbridge.action.SET_RTS"
        const val ACTION_UPDATE_CONFIG = "com.example.espidfandroidbridge.action.UPDATE_CONFIG"
        const val EXTRA_BOOL = "extra_bool"

        fun start(context: Context, action: String, boolValue: Boolean? = null) {
            val intent = Intent(context, BridgeService::class.java).setAction(action)
            if (boolValue != null) intent.putExtra(EXTRA_BOOL, boolValue)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
