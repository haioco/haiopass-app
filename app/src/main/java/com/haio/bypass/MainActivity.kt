package com.haio.bypass

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.haio.bypass.domain.DomainFetcher
import com.haio.bypass.domain.DomainStore
import com.haio.bypass.ui.HaioBypassApp
import com.haio.bypass.ui.screens.SplashScreen
import com.haio.bypass.ui.theme.HaioBypassTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

        val prefs = HaioPrefs(applicationContext)
        val activationManager = ActivationManager(prefs)
        val domainStore = DomainStore(applicationContext)
        val domainFetcher = DomainFetcher()

        setContent {
            HaioBypassTheme {
                var showSplash by remember { mutableStateOf(true) }
                var showApp by remember { mutableStateOf(false) }
                val splashScope = rememberCoroutineScope()

                if (showSplash) {
                    SplashScreen(
                        onSplashFinished = {
                            splashScope.launch {
                                val result = activationManager.run()
                                when (result) {
                                    is ActivationManager.ActivationResult.Success -> {
                                        result.parsedConfig?.let { parsed ->
                                            val configManager = com.haio.bypass.config.ConfigManager(applicationContext)
                                            configManager.updateConfig {
                                                it.copy(
                                                    trojanUrl = result.configUrl,
                                                    trojanConfig = parsed
                                                )
                                            }
                                        }
                                    }
                                    is ActivationManager.ActivationResult.AlreadyActivated -> {}
                                    is ActivationManager.ActivationResult.Error -> {}
                                }
                            }
                            splashScope.launch {
                                val domains = domainFetcher.fetch()
                                if (domains.isNotEmpty()) {
                                    domainStore.setDomains(domains)
                                }
                            }
                            showSplash = false
                            showApp = true
                        }
                    )
                }

                if (showApp) {
                    HaioBypassApp(
                        prefs = prefs,
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