package com.kleber.radar.util

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.kleber.radar.data.model.UserSettings
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "radar_settings")

class SettingsManager(private val context: Context) {

    companion object {
        val DAILY_GOAL = doublePreferencesKey("daily_goal")
        val MIN_PER_KM = doublePreferencesKey("min_per_km")
        val MIN_PER_HOUR = doublePreferencesKey("min_per_hour")
        val FUEL_COST = doublePreferencesKey("fuel_cost")
        val MAINTENANCE_COST = doublePreferencesKey("maintenance_cost")
        val INSURANCE_COST = doublePreferencesKey("insurance_cost")
        val OTHER_COSTS = doublePreferencesKey("other_costs")
        val FUEL_TYPE = stringPreferencesKey("fuel_type")
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
    }

    suspend fun getSettings(): UserSettings {
        val prefs = context.dataStore.data.first()
        return UserSettings(
            dailyGoal = prefs[DAILY_GOAL] ?: 200.0,
            minEarningsPerKm = prefs[MIN_PER_KM] ?: 1.80,
            minEarningsPerHour = prefs[MIN_PER_HOUR] ?: 25.0,
            fuelCostPerKm = prefs[FUEL_COST] ?: 0.45,
            maintenanceCostPerKm = prefs[MAINTENANCE_COST] ?: 0.12,
            insuranceCostPerMonth = prefs[INSURANCE_COST] ?: 300.0,
            otherCostsPerMonth = prefs[OTHER_COSTS] ?: 100.0,
            vehicleFuelType = prefs[FUEL_TYPE] ?: "flex",
            voiceNotificationEnabled = prefs[VOICE_ENABLED] ?: true,
            overlayEnabled = prefs[OVERLAY_ENABLED] ?: true,
            darkMode = prefs[DARK_MODE] ?: true
        )
    }

    suspend fun saveSettings(settings: UserSettings) {
        context.dataStore.edit { prefs ->
            prefs[DAILY_GOAL] = settings.dailyGoal
            prefs[MIN_PER_KM] = settings.minEarningsPerKm
            prefs[MIN_PER_HOUR] = settings.minEarningsPerHour
            prefs[FUEL_COST] = settings.fuelCostPerKm
            prefs[MAINTENANCE_COST] = settings.maintenanceCostPerKm
            prefs[INSURANCE_COST] = settings.insuranceCostPerMonth
            prefs[OTHER_COSTS] = settings.otherCostsPerMonth
            prefs[FUEL_TYPE] = settings.vehicleFuelType
            prefs[VOICE_ENABLED] = settings.voiceNotificationEnabled
            prefs[OVERLAY_ENABLED] = settings.overlayEnabled
            prefs[DARK_MODE] = settings.darkMode
        }
    }
}
