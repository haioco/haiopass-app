package com.haio.bypass.config

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ConfigManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("haio_config", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    fun updateConfig(transform: (AppConfig) -> AppConfig) {
        val newConfig = transform(_config.value)
        _config.value = newConfig
        saveConfig(newConfig)
    }

    fun getConfig(): AppConfig = _config.value

    private fun loadConfig(): AppConfig {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return AppConfig()
        return try {
            json.decodeFromString<AppConfig>(raw)
        } catch (_: Exception) {
            AppConfig()
        }
    }

    private fun saveConfig(config: AppConfig) {
        val raw = json.encodeToString(config)
        prefs.edit().putString(KEY_CONFIG, raw).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
        _config.value = AppConfig()
    }

    companion object {
        private const val KEY_CONFIG = "app_config"
    }
}
