package com.kleber.radar.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.kleber.radar.data.db.RadarDatabase
import com.kleber.radar.data.repository.TripRepository
import com.kleber.radar.service.OverlayService
import com.kleber.radar.util.SettingsManager
import com.kleber.radar.util.TripAnalyzer
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RadarAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var tts: TextToSpeech
    private lateinit var repository: TripRepository
    private lateinit var settingsManager: SettingsManager
    private var lastProcessedText = ""
    private var lastDebugTimestamp = 0L

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
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED

            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100

            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }

        debugLog("=== SERVICO INICIADO ===")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val pkg = event.packageName?.toString() ?: "sem_pacote"
        val now = System.currentTimeMillis()

        if (now - lastDebugTimestamp > 2000) {
            lastDebugTimestamp = now
            debugLog("EVENTO RECEBIDO | package=$pkg | type=${event.eventType}")
        }

        val isDriverApp =
            pkg.contains("ubercab", ignoreCase = true) ||
            pkg.contains("uber", ignoreCase = true) ||
            pkg.contains("99taxis", ignoreCase = true) ||
            pkg.contains("indriver", ignoreCase = true)

        if (!isDriverApp) return

        val allText = mutableListOf<String>()

        try {
            rootInActiveWindow?.let { root ->
                collectText(root, allText)
            }
        } catch (e: Exception) {
            debugLog("ERRO lendo rootInActiveWindow: ${e.message}")
        }

        try {
            windows?.forEach { window ->
                window.root?.let { root ->
                    collectText(root, allText)
                }
            }
        } catch (e: Exception) {
            debugLog("ERRO lendo windows: ${e.message}")
        }

        val fullText = allText
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" | ")

        if (fullText.isBlank()) {
            debugLog("PACKAGE: $pkg | SEM TEXTO CAPTURADO")
            return
        }

        debugLog("PACKAGE: $pkg\nTEXTO CAPTURADO:\n$fullText")

        val tripData = extractTripData(fullText)

        if (tripData == null) {
            debugLog("NAO EXTRAIU CORRIDA DO TEXTO ACIMA")
            return
        }

        val key = "${tripData.value}-${tripData.distance}-${tripData.minutes}"

        if (key == lastProcessedText) return
        lastProcessedText = key

        debugLog("EXTRAIU: valor=${tripData.value} dist=${tripData.distance}km min=${tripData.minutes}")

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

            repository.insert(trip)

            if (settings.voiceNotificationEnabled) {
                tts.speak(
                    TripAnalyzer.gradeLabel(trip.grade),
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    null
                )
            }

            if (settings.overlayEnabled) {
                val intent = Intent(this@RadarAccessibilityService, OverlayService::class.java).apply {
                    putExtra("grade", trip.grade.name)
                    putExtra("earnings_km", trip.earningsPerKm)
                    putExtra("earnings_hour", trip.earningsPerHour)
                    putExtra("net_profit", trip.netProfit)
                    putExtra("distance", trip.distanceKm)
                    putExtra("minutes", trip.estimatedMinutes)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
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

    private fun extractTripData(fullText: String): RawTripData? {
        return try {
            val text = fullText
                .replace("|", " ")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            debugLog("TEXTO NORMALIZADO:\n$text")

            val valueRegex = Regex("""R\$\s*([\d.]+,\d{2}|[\d]+[.,]\d{2})""")
            val minuteRegex = Regex("""(\d+)\s*min(?:uto)?s?""", RegexOption.IGNORE_CASE)
            val kmRegex = Regex("""([\d]+(?:[,.]\d+)?)\s*km""", RegexOption.IGNORE_CASE)

            val value = valueRegex.find(text)
                ?.groupValues
                ?.get(1)
                ?.replace(".", "")
                ?.replace(",", ".")
                ?.toDoubleOrNull()
                ?: return null

            val minutes = minuteRegex.find(text)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: return null

            val distance = kmRegex.findAll(text)
                .mapNotNull {
                    it.groupValues.getOrNull(1)
                        ?.replace(",", ".")
                        ?.toDoubleOrNull()
                }
                .lastOrNull()
                ?: return null

            RawTripData(
                value = value,
                distance = distance,
                minutes = minutes,
                origin = "",
                dest = ""
            )

        } catch (e: Exception) {
            debugLog("ERRO extractTripData: ${e.message}")
            null
        }
    }

    private fun collectText(node: AccessibilityNodeInfo, list: MutableList<String>) {
        try {
            node.text?.toString()?.let {
                if (it.isNotBlank()) list.add(it)
            }

            node.contentDescription?.toString()?.let {
                if (it.isNotBlank()) list.add(it)
            }

            node.viewIdResourceName?.toString()?.let {
                if (it.isNotBlank()) list.add("VIEW_ID:$it")
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    collectText(child, list)
                }
            }
        } catch (e: Exception) {
            debugLog("ERRO collectText: ${e.message}")
        }
    }

    private fun debugLog(message: String) {
        try {
            val debugFile = File(applicationContext.getExternalFilesDir(null), "radar_debug.txt")
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            debugFile.appendText("\n[$timestamp] $message\n")

            if (debugFile.length() > 500_000) {
                val keep = debugFile.readText().takeLast(250_000)
                debugFile.writeText(keep)
            }
        } catch (_: Exception) {
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        debugLog("=== SERVICO PARADO ===")
        scope.cancel()

        if (::tts.isInitialized) {
            tts.shutdown()
        }
    }
}
