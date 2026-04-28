package com.kleber.radar.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.kleber.radar.data.model.Trip

@Dao
interface TripDao {

    @Insert
    suspend fun insert(trip: Trip): Long

    @Update
    suspend fun update(trip: Trip)

    @Delete
    suspend fun delete(trip: Trip)

    @Query("SELECT * FROM trips ORDER BY timestamp DESC")
    fun getAllTrips(): LiveData<List<Trip>>

    @Query("SELECT * FROM trips WHERE timestamp >= :startOfDay ORDER BY timestamp DESC")
    fun getTodayTrips(startOfDay: Long): LiveData<List<Trip>>

    @Query("SELECT * FROM trips WHERE timestamp >= :startOfWeek ORDER BY timestamp DESC")
    fun getWeekTrips(startOfWeek: Long): LiveData<List<Trip>>

    @Query("SELECT * FROM trips WHERE timestamp >= :startOfMonth ORDER BY timestamp DESC")
    fun getMonthTrips(startOfMonth: Long): LiveData<List<Trip>>

    @Query("SELECT SUM(grossValue) FROM trips WHERE timestamp >= :startOfDay")
    fun getTodayGross(startOfDay: Long): LiveData<Double?>

    @Query("SELECT SUM(netProfit) FROM trips WHERE timestamp >= :startOfDay")
    fun getTodayNet(startOfDay: Long): LiveData<Double?>

    @Query("SELECT COUNT(*) FROM trips WHERE timestamp >= :startOfDay")
    fun getTodayCount(startOfDay: Long): LiveData<Int>

    @Query("SELECT AVG(earningsPerHour) FROM trips WHERE timestamp >= :startOfDay")
    fun getTodayAvgPerHour(startOfDay: Long): LiveData<Double?>

    @Query("SELECT * FROM trips ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastTrip(): Trip?
}
