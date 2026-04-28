package com.kleber.radar.service

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.kleber.radar.R
import com.kleber.radar.data.model.TripGrade
import com.kleber.radar.ui.MainActivity

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { removeOverlay() }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY

        val grade = intent.getStringExtra("grade")?.let { TripGrade.valueOf(it) } ?: return START_NOT_STICKY
        val earningsKm = intent.getDoubleExtra("earnings_km", 0.0)
        val earningsHour = intent.getDoubleExtra("earnings_hour", 0.0)
        val netProfit = intent.getDoubleExtra("net_profit", 0.0)
        val distance = intent.getDoubleExtra("distance", 0.0)
        val minutes = intent.getIntExtra("minutes", 0)

        showOverlay(grade, earningsKm, earningsHour, netProfit, distance, minutes)
        return START_NOT_STICKY
    }

    private fun showOverlay(
        grade: TripGrade,
        earningsKm: Double,
        earningsHour: Double,
        netProfit: Double,
        distance: Double,
        minutes: Int
    ) {
        removeOverlay()

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_trip, null)

        val bgColor = when (grade) {
            TripGrade.GREEN -> Color.parseColor("#CC1B5E20")
            TripGrade.YELLOW -> Color.parseColor("#CCB45309")
            TripGrade.RED -> Color.parseColor("#CC7F1D1D")
        }
        view.setBackgroundColor(bgColor)

        view.findViewById<TextView>(R.id.tv_grade).text = when (grade) {
            TripGrade.GREEN -> "✅ VALE A PENA"
            TripGrade.YELLOW -> "⚠️ ANALISAR"
            TripGrade.RED -> "❌ RECUSAR"
        }
        view.findViewById<TextView>(R.id.tv_per_km).text = "R$/km: %.2f".format(earningsKm)
        view.findViewById<TextView>(R.id.tv_per_hour).text = "R$/h: %.2f".format(earningsHour)
        view.findViewById<TextView>(R.id.tv_net).text = "Lucro: R$ %.2f".format(netProfit)
        view.findViewById<TextView>(R.id.tv_info).text = "%.1f km · %d min".format(distance, minutes)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 100
        }

        windowManager.addView(view, params)
        overlayView = view

        // Remove overlay após 8 segundos
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 8000)
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
            overlayView = null
        }
    }

    private fun startForegroundNotification() {
        val channelId = "radar_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Radar Overlay", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Radar ativo")
            .setContentText("Monitorando corridas...")
            .setSmallIcon(R.drawable.ic_radar)
            .setContentIntent(
                PendingIntent.getActivity(this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE)
            )
            .build()
        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }
}
