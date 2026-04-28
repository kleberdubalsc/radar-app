package com.kleber.radar.data.db

import androidx.room.TypeConverter
import com.kleber.radar.data.model.TripGrade

class Converters {
    @TypeConverter
    fun fromGrade(grade: TripGrade): String = grade.name

    @TypeConverter
    fun toGrade(value: String): TripGrade = TripGrade.valueOf(value)
}
