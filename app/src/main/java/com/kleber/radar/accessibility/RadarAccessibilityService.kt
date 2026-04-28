package com.kleber.radar.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.kleber.radar.data.db.RadarDatabase
import com.kleber.radar.data.model.UserSettings
import com.kleber.radar.data.repository.TripRepository
import com.kleber.radar.service.OverlayService
import com.kleber.radar.util.TripAnalyzer
import com.kleber.radar.util.SettingsManager
import kotlinx.coroutines.*
import java.util.Locale

class RadarAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var tts: TextToSpeech
    private lateinit var repository: TripRepository
    private lateinit var settingsManager: SettingsManager
    private var lastProcessedText = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = TripRepository(RadarDatabase.getInstance(this).tripDao())
        settingsManager = SettingsManager(this)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale("pt", "BR")
            }
        }

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                         AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            packageNames = arrayOf(
                "com.ubercab.driver",
                "com.ubercab.driver.feature.trip"
            )
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val root = rootInActiveWindow ?: return

        // Tenta extrair dados da oferta de corrida
        val tripData = extractTripData(root) ?: return

        // Evita processar a mesma oferta duas vezes
        val key = "${tripData.value}-${tripData.distance}"
        if (key == lastProcessedText) return
        lastProcessedText = key

        scope.launch {
            val settings = settingsManager.getSettings()
            val trip = TripAnalyzer.analyze(
                grossValue = tripData.value,
                distanceKm = tripData.distance,
                estimatedMinutes = tripData.minutes,
                originArea = tripData.origin,
                destArea = tripData.dest,
                settings = settings
            )

            // Salva no banco
            repository.insert(trip)

            // Notificação por voz
            if (settings.voiceNotificationEnabled) {
                val label = TripAnalyzer.gradeLabel(trip.grade)
                tts.speak(label, TextToSpeech.QUEUE_FLUSH, null, null)
            }

            // Mostra overlay na tela
            if (settings.overlayEnabled) {
                val intent = Intent(this@RadarAccessibilityService, OverlayService::class.java).apply {
                    putExtra("grade", trip.grade.name)
                    putExtra("earnings_km", trip.earningsPerKm)
                    putExtra("earnings_hour", trip.earningsPerHour)
                    putExtra("net_profit", trip.netProfit)
                    putExtra("distance", trip.distanceKm)
                    putExtra("minutes", trip.estimatedMinutes)
                }
                startService(intent)
            }
        }
    }

    private data class RawTripData(
        val value: Double,
        val distance: Double,
        val minutes: Int,
        val origin: String,
        val dest: String
    )

    private fun extractTripData(root: AccessibilityNodeInfo): RawTripData? {
        return try {
            val allText = mutableListOf<String>()
            collectText(root, allText)
            val fullText = allText.joinToString(" ")

            // Padrões típicos da tela do Uber Driver
            val valueRegex = Regex("""R\$\s*([\d,]+(?:\.\d{2})?)""")
            val distanceRegex = Regex("""([\d,]+(?:\.\d+)?)\s*km""")
            val minuteRegex = Regex("""(\d+)\s*min""")

            val value = valueRegex.find(fullText)?.groupValues?.get(1)
                ?.replace(",", ".")?.toDoubleOrNull() ?: return null
            val distance = distanceRegex.find(fullText)?.groupValues?.get(1)
                ?.replace(",", ".")?.toDoubleOrNull() ?: return null
            val minutes = minuteRegex.find(fullText)?.groupValues?.get(1)
                ?.toIntOrNull() ?: 10

            RawTripData(value, distance, minutes, "", "")
        } catch (e: Exception) {
            null
        }
    }

    private fun collectText(node: AccessibilityNodeInfo, list: MutableList<String>) {
        node.text?.toString()?.let { if (it.isNotBlank()) list.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, list) }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        if (::tts.isInitialized) tts.shutdown()
    }
}
