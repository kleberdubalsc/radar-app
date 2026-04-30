package com.kleber.radar.ui.history

import android.graphics.Color
import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kleber.radar.R
import com.kleber.radar.data.model.Trip
import com.kleber.radar.data.model.TripGrade
import java.text.SimpleDateFormat
import java.util.*

class TripAdapter : ListAdapter<Trip, TripAdapter.TripViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    inner class TripViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvGrade: TextView = view.findViewById(R.id.tv_grade)
        val tvValue: TextView = view.findViewById(R.id.tv_value)
        val tvInfo: TextView = view.findViewById(R.id.tv_info)
        val tvTime: TextView = view.findViewById(R.id.tv_time)
        val tvNet: TextView = view.findViewById(R.id.tv_net)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trip, parent, false)
        return TripViewHolder(view)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        val trip = getItem(position)

        val (emoji, color) = when (trip.grade) {
            TripGrade.GREEN -> Pair("✅", Color.parseColor("#1B5E20"))
            TripGrade.YELLOW -> Pair("⚠️", Color.parseColor("#B45309"))
            TripGrade.RED -> Pair("❌", Color.parseColor("#7F1D1D"))
        }

        holder.tvGrade.text = emoji
        holder.tvValue.text = "R$ %.2f".format(trip.grossValue)
        holder.tvNet.text = "Lucro: R$ %.2f".format(trip.netProfit)
        holder.tvInfo.text = "%.1f km · %d min · R$%.2f/km".format(
            trip.distanceKm, trip.estimatedMinutes, trip.earningsPerKm)
        holder.tvTime.text = dateFormat.format(Date(trip.timestamp))
        holder.itemView.setBackgroundColor(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)))
    }

    class DiffCallback : DiffUtil.ItemCallback<Trip>() {
        override fun areItemsTheSame(oldItem: Trip, newItem: Trip) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Trip, newItem: Trip) = oldItem == newItem
    }
}
