package com.haio.bypass

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.haio.bypass.ui.HaioBypassApp
import com.haio.bypass.ui.screens.SplashScreen
import com.haio.bypass.ui.theme.HaioBypassTheme

class MainActivity : ComponentActivity() {

    private var vpnPermissionResult: ((Boolean) -> Unit)? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val granted = result.resultCode == RESULT_OK
        vpnPermissionResult?.invoke(granted)
        vpnPermissionResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HaioBypassTheme {
                var showSplash by remember { mutableStateOf(true) }
                var showApp by remember { mutableStateOf(false) }

                if (showSplash) {
                    SplashScreen(onSplashFinished = {
                        showSplash = false
                        showApp = true
                    })
                }

                if (showApp) {
                    HaioBypassApp(
                        requestVpnPermission = { callback ->
                            requestVpnPermission(callback)
                        }
                    )
                }
            }
        }
    }

    private fun requestVpnPermission(callback: (Boolean) -> Unit) {
        val intent = VpnService.prepare(this)
        if (intent == null) {
            callback(true)
        } else {
            vpnPermissionResult = callback
            vpnPermissionLauncher.launch(intent)
        }
    }
}