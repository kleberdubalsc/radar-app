package com.kleber.radar.ui.home

import android.app.Application
import androidx.lifecycle.*
import com.kleber.radar.data.db.RadarDatabase
import com.kleber.radar.data.repository.TripRepository

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TripRepository(RadarDatabase.getInstance(application).tripDao())

    val todayTrips = repository.getTodayTrips()
    val todayGross = repository.getTodayGross()
    val todayNet = repository.getTodayNet()
    val todayCount = repository.getTodayCount()
    val todayAvgPerHour = repository.getTodayAvgPerHour()
}
