package org.localsend.localsend_app.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class AppSettings(
    val alias: String = "Android",
    val port: Int = 53317,
    val fingerprint: String = UUID.randomUUID().toString().replace("-", "").take(16),
    val theme: String = "system",
    val colorMode: String = "system",
    val locale: String = "en",
    val saveToGallery: Boolean = true,
    val saveToHistory: Boolean = true,
    val quickSave: Boolean = false,
    val quickSaveFromFavorites: Boolean = false,
    val receivePin: String? = null,
    val autoFinish: Boolean = true,
    val https: Boolean = false,
    val sendMode: String = "multiple",
    val enableAnimations: Boolean = true,
    val deviceType: String = "mobile",
    val deviceModel: String = "Android",
    val advancedSettings: Boolean = false,
    val networkWhitelist: List<String> = emptyList(),
    val networkBlacklist: List<String> = emptyList(),
    val multicastGroup: String = "224.0.0.167",
    val discoveryTimeout: Int = 500
)

class SettingsManager {
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    
    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
    }
    
    fun updateAlias(alias: String) {
        _settings.value = _settings.value.copy(alias = alias)
    }
    
    fun updatePort(port: Int) {
        _settings.value = _settings.value.copy(port = port)
    }
    
    fun updateTheme(theme: String) {
        _settings.value = _settings.value.copy(theme = theme)
    }
    
    fun updateQuickSave(quickSave: Boolean) {
        _settings.value = _settings.value.copy(quickSave = quickSave)
    }
    
    fun updateSaveToGallery(saveToGallery: Boolean) {
        _settings.value = _settings.value.copy(saveToGallery = saveToGallery)
    }
    
    fun updateHttps(https: Boolean) {
        _settings.value = _settings.value.copy(https = https)
    }
    
    fun updateReceivePin(pin: String?) {
        _settings.value = _settings.value.copy(receivePin = pin)
    }
}
