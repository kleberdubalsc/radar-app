package com.kleber.radar.data.model

data class UserSettings(
    // metas financeiras
    val dailyGoal: Double = 200.0,
    val minEarningsPerKm: Double = 1.80,
    val minEarningsPerHour: Double = 25.0,

    // custos do veículo
    val fuelCostPerKm: Double = 0.45,       // R$/km com combustível
    val maintenanceCostPerKm: Double = 0.12, // R$/km manutenção
    val insuranceCostPerMonth: Double = 300.0,
    val otherCostsPerMonth: Double = 100.0,

    // veículo
    val vehicleFuelType: String = "flex",   // flex, gasolina, etanol, eletrico
    val batteryCapacityKwh: Double = 0.0,   // só para elétricos

    // preferências
    val voiceNotificationEnabled: Boolean = true,
    val overlayEnabled: Boolean = true,
    val darkMode: Boolean = true
)
