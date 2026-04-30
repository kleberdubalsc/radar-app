package com.kleber.radar.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TripGrade { GREEN, YELLOW, RED }

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val grossValue: Double,        // valor bruto da corrida
    val distanceKm: Double,        // distância em km
    val estimatedMinutes: Int,     // tempo estimado em minutos
    val originArea: String = "",   // bairro/zona de origem
    val destArea: String = "",     // bairro/zona de destino
    val grade: TripGrade,          // GREEN / YELLOW / RED
    val earningsPerKm: Double,     // R$/km
    val earningsPerHour: Double,   // R$/hora
    val netProfit: Double,         // lucro real após custos
    val accepted: Boolean = false  // se o motorista aceitou
)
