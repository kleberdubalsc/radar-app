package com.kleber.radar.data.repository

import androidx.lifecycle.LiveData
import com.kleber.radar.data.db.TripDao
import com.kleber.radar.data.model.Trip
import java.util.Calendar

class TripRepository(private val dao: TripDao) {

    fun getAllTrips(): LiveData<List<Trip>> = dao.getAllTrips()
    fun getTodayTrips(): LiveData<List<Trip>> = dao.getTodayTrips(startOfToday())
    fun getWeekTrips(): LiveData<List<Trip>> = dao.getWeekTrips(startOfWeek())
    fun getMonthTrips(): LiveData<List<Trip>> = dao.getMonthTrips(startOfMonth())

    fun getTodayGross(): LiveData<Double?> = dao.getTodayGross(startOfToday())
    fun getTodayNet(): LiveData<Double?> = dao.getTodayNet(startOfToday())
    fun getTodayCount(): LiveData<Int> = dao.getTodayCount(startOfToday())
    fun getTodayAvgPerHour(): LiveData<Double?> = dao.getTodayAvgPerHour(startOfToday())

    suspend fun insert(trip: Trip) = dao.insert(trip)
    suspend fun update(trip: Trip) = dao.update(trip)
    suspend fun delete(trip: Trip) = dao.delete(trip)
    suspend fun getLastTrip() = dao.getLastTrip()

    private fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun startOfWeek(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun startOfMonth(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
