package com.haio.bypass.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.haio.bypass.config.ConfigManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val configManager = ConfigManager(context)
        if (configManager.getConfig().autostart) {
            Log.i(TAG, "Auto-starting VPN on boot")
            val serviceIntent = Intent(context, HaioVpnService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
