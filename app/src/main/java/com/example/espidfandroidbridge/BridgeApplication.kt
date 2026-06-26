package com.example.espidfandroidbridge

import android.app.Application
import com.example.espidfandroidbridge.bridge.BridgePreferences
import com.example.espidfandroidbridge.bridge.BridgeStateStore

class BridgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BridgeStateStore.setConfig(BridgePreferences.load(this))
    }
}
