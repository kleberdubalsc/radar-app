package com.kleber.radar.ui.history

import android.app.Application
import androidx.lifecycle.*
import com.kleber.radar.R
import com.kleber.radar.data.db.RadarDatabase
import com.kleber.radar.data.model.Trip
import com.kleber.radar.data.repository.TripRepository
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TripRepository(RadarDatabase.getInstance(application).tripDao())
    private val _filter = MutableLiveData<Int?>(null)

    val allTrips: LiveData<List<Trip>> = _filter.switchMap { filterId ->
        when (filterId) {
            R.id.chip_week -> repository.getWeekTrips()
            R.id.chip_month -> repository.getMonthTrips()
            else -> repository.getTodayTrips()
        }
    }

    fun setFilter(chipId: Int?) {
        _filter.value = chipId
    }

    fun deleteTrip(trip: Trip) = viewModelScope.launch {
        repository.delete(trip)
    }
}
