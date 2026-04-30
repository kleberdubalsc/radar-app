package com.kleber.radar.util

import com.kleber.radar.data.model.Trip
import com.kleber.radar.data.model.TripGrade
import com.kleber.radar.data.model.UserSettings

object TripAnalyzer {

    fun analyze(
        grossValue: Double,
        distanceKm: Double,
        estimatedMinutes: Int,
        originArea: String = "",
        destArea: String = "",
        settings: UserSettings
    ): Trip {
        val estimatedHours = estimatedMinutes / 60.0
        val earningsPerKm = if (distanceKm > 0) grossValue / distanceKm else 0.0
        val earningsPerHour = if (estimatedHours > 0) grossValue / estimatedHours else 0.0

        // custo total da corrida
        val fuelCost = distanceKm * settings.fuelCostPerKm
        val maintenanceCost = distanceKm * settings.maintenanceCostPerKm
        val fixedCostPerMinute = (settings.insuranceCostPerMonth + settings.otherCostsPerMonth) / (30 * 8 * 60)
        val fixedCost = fixedCostPerMinute * estimatedMinutes
        val totalCost = fuelCost + maintenanceCost + fixedCost
        val netProfit = grossValue - totalCost

        val grade = when {
            earningsPerKm >= settings.minEarningsPerKm && earningsPerHour >= settings.minEarningsPerHour -> TripGrade.GREEN
            earningsPerKm < settings.minEarningsPerKm * 0.7 || earningsPerHour < settings.minEarningsPerHour * 0.7 -> TripGrade.RED
            else -> TripGrade.YELLOW
        }

        return Trip(
            grossValue = grossValue,
            distanceKm = distanceKm,
            estimatedMinutes = estimatedMinutes,
            originArea = originArea,
            destArea = destArea,
            grade = grade,
            earningsPerKm = earningsPerKm,
            earningsPerHour = earningsPerHour,
            netProfit = netProfit
        )
    }

    fun gradeLabel(grade: TripGrade): String = when (grade) {
        TripGrade.GREEN -> "✅ VALE A PENA"
        TripGrade.YELLOW -> "⚠️ ANALISAR"
        TripGrade.RED -> "❌ RECUSAR"
    }
}
