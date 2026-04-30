package com.kleber.radar.ui.settings

import android.app.Application
import androidx.lifecycle.*
import com.kleber.radar.data.model.UserSettings
import com.kleber.radar.util.SettingsManager
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = SettingsManager(application)
    private val _settings = MutableLiveData<UserSettings>()
    val settings: LiveData<UserSettings> = _settings

    init {
        viewModelScope.launch {
            _settings.postValue(manager.getSettings())
        }
    }

    fun save(
        dailyGoal: Double, minPerKm: Double, minPerHour: Double,
        fuelCost: Double, maintenance: Double, insurance: Double,
        otherCosts: Double, voiceEnabled: Boolean, overlayEnabled: Boolean
    ) {
        viewModelScope.launch {
            val updated = (_settings.value ?: UserSettings()).copy(
                dailyGoal = dailyGoal,
                minEarningsPerKm = minPerKm,
                minEarningsPerHour = minPerHour,
                fuelCostPerKm = fuelCost,
                maintenanceCostPerKm = maintenance,
                insuranceCostPerMonth = insurance,
                otherCostsPerMonth = otherCosts,
                voiceNotificationEnabled = voiceEnabled,
                overlayEnabled = overlayEnabled
            )
            manager.saveSettings(updated)
            _settings.postValue(updated)
        }
    }
}
