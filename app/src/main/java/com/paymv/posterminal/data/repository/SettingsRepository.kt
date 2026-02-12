package com.paymv.posterminal.data.repository

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import com.paymv.posterminal.data.model.AppSettings
import com.paymv.posterminal.data.model.PaymentReceptionMode
import com.paymv.posterminal.data.service.PayMVFirebaseMessagingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

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
    
    init {
        // Set up FCM token refresh callback
        PayMVFirebaseMessagingService.onTokenRefreshed = { token ->
            val current = _settings.value
            if (current.fcmToken != token) {
                saveSettings(current.copy(fcmToken = token))
                Log.d(TAG, "FCM token updated in settings")
            }
        }
    }
    
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
            browserUrl     = if (s.browserUrl     != null) s.browserUrl     else defaults.browserUrl,
            deviceId       = if (s.deviceId       != null) s.deviceId       else defaults.deviceId,
            fcmToken       = if (s.fcmToken       != null) s.fcmToken       else defaults.fcmToken
        )
    }
    
    fun saveSettings(settings: AppSettings) {
        val json = gson.toJson(settings)
        sharedPreferences.edit().putString("app_settings", json).apply()
        _settings.value = settings
    }
    
    /**
     * Initialize device ID from Firebase Installations API.
     * Called once on first app launch or when device ID is empty.
     */
    suspend fun initializeDeviceId() {
        val current = _settings.value
        if (current.deviceId.isNotEmpty()) return
        
        try {
            val installationId = FirebaseInstallations.getInstance().id.await()
            saveSettings(current.copy(deviceId = installationId))
            Log.d(TAG, "Device ID initialized: $installationId")
            
            // Subscribe to device-specific FCM topic
            subscribeToDeviceTopic(installationId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Firebase Installation ID: ${e.message}")
        }
    }
    
    /**
     * Refresh FCM token and save to settings.
     */
    suspend fun refreshFcmToken() {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            val current = _settings.value
            if (current.fcmToken != token) {
                saveSettings(current.copy(fcmToken = token))
                Log.d(TAG, "FCM token refreshed: ${token.take(20)}...")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh FCM token: ${e.message}")
        }
    }
    
    /**
     * Subscribe to the device-specific FCM topic.
     */
    suspend fun subscribeToDeviceTopic(deviceId: String? = null) {
        try {
            val id = deviceId ?: _settings.value.deviceId
            if (id.isEmpty()) return
            
            val topic = "device_$id"
            FirebaseMessaging.getInstance().subscribeToTopic(topic).await()
            Log.d(TAG, "Subscribed to FCM topic: $topic")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to FCM topic: ${e.message}")
        }
    }
    
    /**
     * Unsubscribe from the device-specific FCM topic.
     */
    suspend fun unsubscribeFromDeviceTopic() {
        try {
            val id = _settings.value.deviceId
            if (id.isEmpty()) return
            
            val topic = "device_$id"
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).await()
            Log.d(TAG, "Unsubscribed from FCM topic: $topic")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unsubscribe from FCM topic: ${e.message}")
        }
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
