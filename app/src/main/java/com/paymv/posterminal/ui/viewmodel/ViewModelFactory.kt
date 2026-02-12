package com.paymv.posterminal.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.paymv.posterminal.data.repository.PaymentRepository
import com.paymv.posterminal.data.repository.SettingsRepository
import com.paymv.posterminal.util.NetworkMonitor

object ViewModelFactory {
    
    fun createIdleViewModelFactory(
        paymentRepository: PaymentRepository,
        settingsRepository: SettingsRepository,
        networkMonitor: NetworkMonitor
    ): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(IdleViewModel::class.java)) {
                    return IdleViewModel(paymentRepository, settingsRepository, networkMonitor) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
    
    fun createQrDisplayViewModelFactory(
        application: Application,
        paymentRepository: PaymentRepository,
        settingsRepository: SettingsRepository,
        amount: String
    ): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(QrDisplayViewModel::class.java)) {
                    return QrDisplayViewModel(application, paymentRepository, settingsRepository, amount) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
    
    fun createSettingsViewModelFactory(
        settingsRepository: SettingsRepository,
        paymentRepository: PaymentRepository
    ): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                    return SettingsViewModel(settingsRepository, paymentRepository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
    
    fun createBrowserViewModelFactory(
        paymentRepository: PaymentRepository,
        settingsRepository: SettingsRepository,
        networkMonitor: NetworkMonitor
    ): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(BrowserViewModel::class.java)) {
                    return BrowserViewModel(paymentRepository, settingsRepository, networkMonitor) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
