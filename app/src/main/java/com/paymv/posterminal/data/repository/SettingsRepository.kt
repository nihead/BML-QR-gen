package com.paymv.posterminal.data.repository

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.paymv.posterminal.data.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    
    companion object {
        private const val TAG = "SettingsRepository"
    }
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "paymv_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    private val gson = Gson()
    
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    
    private fun loadSettings(): AppSettings {
        val json = sharedPreferences.getString("app_settings", null)
        return if (json != null) {
            try {
                val loaded = gson.fromJson(json, AppSettings::class.java)
                // Gson doesn't use Kotlin default parameter values — fields missing
                // from JSON are set to null via reflection even if typed as non-null.
                // Rebuild with safe defaults so newly-added fields never cause NPE.
                sanitizeSettings(loaded)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading settings, using defaults: ${e.message}")
                AppSettings()
            }
        } else {
            AppSettings() // Default settings
        }
    }
    
    /**
     * Ensure all non-null String fields have a value.
     * Gson may leave them as null when deserializing old JSON that predates the field.
     */
    @Suppress("SENSELESS_COMPARISON")
    private fun sanitizeSettings(s: AppSettings): AppSettings {
        val defaults = AppSettings()
        return s.copy(
            storeName      = if (s.storeName      != null) s.storeName      else defaults.storeName,
            accountName    = if (s.accountName    != null) s.accountName    else defaults.accountName,
            accountNumber  = if (s.accountNumber  != null) s.accountNumber  else defaults.accountNumber,
            adminPassword  = if (s.adminPassword  != null) s.adminPassword  else defaults.adminPassword,
            browserUrl     = if (s.browserUrl     != null) s.browserUrl     else defaults.browserUrl
        )
    }
    
    fun saveSettings(settings: AppSettings) {
        val json = gson.toJson(settings)
        sharedPreferences.edit().putString("app_settings", json).apply()
        _settings.value = settings
    }
    
    fun validatePassword(inputPassword: String): Boolean {
        val storedPassword = _settings.value.adminPassword
        // If no password is set, always grant access
        if (storedPassword.isEmpty()) return true
        return storedPassword == inputPassword
    }
    
    fun validateAccountNumber(accountNumber: String): Boolean {
        return accountNumber.length == 13 && accountNumber.all { it.isDigit() }
    }
    
    fun validateMobileNumber(mobileNumber: String?): Boolean {
        if (mobileNumber.isNullOrEmpty()) return true // Optional field
        val regex = Regex("""\+960\d{7}""")
        return regex.matches(mobileNumber)
    }
    
    fun validateWebhookPort(port: Int): Boolean {
        return port in 1024..65535
    }
    
    fun validatePassword(password: String, isNewPassword: Boolean = false): Boolean {
        if (isNewPassword) {
            return password.length >= 6
        }
        return validatePassword(password)
    }
}
