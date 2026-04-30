package com.kleber.radar.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kleber.radar.data.model.Trip

@Database(entities = [Trip::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class RadarDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        @Volatile private var INSTANCE: RadarDatabase? = null

        fun getInstance(context: Context): RadarDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RadarDatabase::class.java,
                    "radar_database"
                ).build().also { INSTANCE = it }
            }
    }
}
