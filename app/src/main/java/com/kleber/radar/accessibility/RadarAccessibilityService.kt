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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private var pollingJob: Job? = null

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
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED

            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50

            flags =
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }

        debugLog("=== SERVICO INICIADO ===")
        startScreenPolling()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val pkg = event.packageName?.toString() ?: "sem_pacote"
        val now = System.currentTimeMillis()

        if (now - lastDebugTimestamp > 1500) {
            lastDebugTimestamp = now
            debugLog("EVENTO RECEBIDO | package=$pkg | type=${event.eventType}")
        }

        val isRelevantPackage =
            pkg.contains("ubercab", ignoreCase = true) ||
            pkg.contains("uber", ignoreCase = true) ||
            pkg.contains("whatsapp", ignoreCase = true) ||
            pkg.contains("photos", ignoreCase = true) ||
            pkg.contains("gallery", ignoreCase = true) ||
            pkg.contains("android.systemui", ignoreCase = true)

        if (isRelevantPackage) {
            processCurrentWindow("EVENTO:$pkg")
        }
    }

    private fun startScreenPolling() {
        if (pollingJob != null) return

        pollingJob = scope.launch {
            debugLog("POLLING INICIADO")

            while (isActive) {
                try {
                    delay(300)
                    processCurrentWindow("POLLING")
                } catch (e: Exception) {
                    debugLog("ERRO polling: ${e.message}")
                }
            }
        }
    }

    private fun processCurrentWindow(source: String) {
        val allText = mutableListOf<String>()

        try {
            rootInActiveWindow?.let { root ->
                collectText(root, allText)
            }
        } catch (e: Exception) {
            debugLog("ERRO lendo rootInActiveWindow [$source]: ${e.message}")
        }

        try {
            windows?.forEach { window ->
                window.root?.let { root ->
                    collectText(root, allText)
                }
            }
        } catch (e: Exception) {
            debugLog("ERRO lendo windows [$source]: ${e.message}")
        }

        val fullText = allText
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" | ")

        if (fullText.isBlank()) return

        val looksLikeTripScreen =
            fullText.contains("R$", ignoreCase = true) &&
            (
                fullText.contains("Viagem de", ignoreCase = true) ||
                fullText.contains("Selecionar", ignoreCase = true) ||
                fullText.contains("Aceitar", ignoreCase = true) ||
                fullText.contains("UberX", ignoreCase = true) ||
                fullText.contains("km", ignoreCase = true)
            )

        if (!looksLikeTripScreen) return

        debugLog("[$source] TEXTO CAPTURADO:\n$fullText")

        val tripData = extractTripData(fullText)

        if (tripData == null) {
            debugLog("[$source] NAO EXTRAIU CORRIDA DO TEXTO ACIMA")
            return
        }

        val key = "${tripData.value}-${tripData.distance}-${tripData.minutes}"

        if (key == lastProcessedText) return
        lastProcessedText = key

        debugLog(
            "[$source] EXTRAIU: valor=${tripData.value} dist=${tripData.distance}km min=${tripData.minutes}"
        )

        analyzeAndShowTrip(tripData)
    }

    private fun analyzeAndShowTrip(tripData: RawTripData) {
        scope.launch {
            try {
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

                if (settings.voiceNotificationEnabled && ::tts.isInitialized) {
                    tts.speak(
                        TripAnalyzer.gradeLabel(trip.grade),
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        null
                    )
                }

                if (settings.overlayEnabled) {
                    val intent = Intent(
                        this@RadarAccessibilityService,
                        OverlayService::class.java
                    ).apply {
                        putExtra("grade", trip.grade.name)
                        putExtra("earnings_km", trip.earningsPerKm)
                        putExtra("earnings_hour", trip.earningsPerHour)
                        putExtra("net_profit", trip.netProfit)
                        putExtra("distance", trip.distanceKm)
                        putExtra("minutes", trip.estimatedMinutes)
                    }

                    debugLog("INICIANDO OVERLAY grade=${trip.grade.name}")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                } else {
                    debugLog("OVERLAY DESATIVADO NAS CONFIGURACOES")
                }

            } catch (e: Exception) {
                debugLog("ERRO analyzeAndShowTrip: ${e.message}")
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

            val valueRegex = Regex(
                """R\$\s*([\d.]+,\d{2}|[\d]+[.,]\d{2})""",
                RegexOption.IGNORE_CASE
            )

            val values = valueRegex.findAll(text)
                .mapNotNull { match ->
                    match.groupValues.getOrNull(1)
                        ?.replace(".", "")
                        ?.replace(",", ".")
                        ?.toDoubleOrNull()
                }
                .filter { it > 0.0 }
                .toList()

            val value = values.lastOrNull() ?: return null

            val tripRegex = Regex(
                """Viagem\s+de\s+(?:(\d+)\s*h\s*(?:e\s*)?)?(?:(\d+)\s*min(?:uto)?s?)?\s*\(?\s*([\d]+(?:[,.]\d+)?)\s*km\s*\)?""",
                RegexOption.IGNORE_CASE
            )

            val tripMatch = tripRegex.find(text) ?: return null

            val hours = tripMatch.groupValues.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
                ?.toIntOrNull()
                ?: 0

            val minutesOnly = tripMatch.groupValues.getOrNull(2)
                ?.takeIf { it.isNotBlank() }
                ?.toIntOrNull()
                ?: 0

            val totalMinutes = (hours * 60) + minutesOnly

            val distance = tripMatch.groupValues.getOrNull(3)
                ?.replace(",", ".")
                ?.toDoubleOrNull()
                ?: return null

            if (totalMinutes <= 0 || distance <= 0.0) return null

            debugLog("REGEX OK: valor=$value minutos=$totalMinutes distancia=$distance")

            RawTripData(
                value = value,
                distance = distance,
                minutes = totalMinutes,
                origin = extractOrigin(text),
                dest = extractDestination(text)
            )

        } catch (e: Exception) {
            debugLog("ERRO extractTripData: ${e.message}")
            null
        }
    }

    private fun extractOrigin(text: String): String {
        return try {
            val pickupRegex = Regex(
                """(?:de distância)\s+(.+?)\s+Viagem de""",
                RegexOption.IGNORE_CASE
            )

            pickupRegex.find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractDestination(text: String): String {
        return try {
            val destinationRegex = Regex(
                """Viagem\s+de\s+(?:(?:\d+)\s*h\s*(?:e\s*)?)?(?:(?:\d+)\s*min(?:uto)?s?)?\s*\(?\s*(?:[\d]+(?:[,.]\d+)?)\s*km\s*\)?\s+(.+?)(?:\s+Selecionar|\s+Aceitar|\s+VIEW_ID:|$)""",
                RegexOption.IGNORE_CASE
            )

            destinationRegex.find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?: ""
        } catch (_: Exception) {
            ""
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
                    child.recycle()
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

            if (debugFile.length() > 800_000) {
                val keep = debugFile.readText().takeLast(400_000)
                debugFile.writeText(keep)
            }
        } catch (_: Exception) {
        }
    }

    override fun onInterrupt() {
        debugLog("=== SERVICO INTERROMPIDO ===")
    }

    override fun onDestroy() {
        super.onDestroy()

        debugLog("=== SERVICO PARADO ===")

        pollingJob?.cancel()
        pollingJob = null

        scope.cancel()

        if (::tts.isInitialized) {
            tts.shutdown()
        }
    }
}
