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
import com.kleber.radar.util.AccessibilityDebugStore
import com.kleber.radar.util.SettingsManager
import com.kleber.radar.util.TripAnalyzer
import com.kleber.radar.util.UberOfferParser
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
    private var lastFailedParserText = ""
    private var pollingJob: Job? = null
    private var lastIdleHeartbeatAt = 0L

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

        AccessibilityDebugStore.markServiceConnected(this)
        debugLog(
            "=== SERVICO INICIADO ===\n" +
                "canRetrieveWindowContent=true | eventTypes=${serviceInfo.eventTypes} | " +
                "flags=${serviceInfo.flags} | notificationTimeout=${serviceInfo.notificationTimeout}"
        )
        startScreenPolling()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val pkg = event.packageName?.toString() ?: "sem_pacote"
        val className = event.className?.toString() ?: "sem_classe"
        val eventType = event.eventType
        if (!pkg.equals(packageName, ignoreCase = true)) {
            AccessibilityDebugStore.saveEvent(this, pkg, className, eventType)
        }

        if (isRideAppPackage(pkg)) {
            debugLog("ON_ACCESSIBILITY_EVENT | type=$eventType | package=$pkg | class=$className")
        }

        processCurrentWindow(
            source = "EVENTO:$pkg",
            eventPackage = pkg,
            eventClassName = className,
            eventType = eventType
        )
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

    private fun isRideAppPackage(pkg: String): Boolean {
        val p = pkg.lowercase(Locale.getDefault())
        return p.contains("ubercab") ||
            p.contains("uber") ||
            p.contains("99taxis") ||
            p.contains("99app") ||
            p.contains("indriver") ||
            p.contains("indrive")
    }

    /**
     * Varredura completa da árvore de acessibilidade só roda quando um app de corrida
     * (Uber/99/InDriver) está realmente na tela. Para qualquer outro app, o custo desta
     * função é só ler o nome do pacote (sem percorrer a árvore de nós) — é isso que evita
     * o app pesar o celular inteiro enquanto o motorista usa outros aplicativos.
     */
    private fun processCurrentWindow(
        source: String,
        eventPackage: String = "POLLING",
        eventClassName: String = "POLLING",
        eventType: Int = -1
    ) {
        val relevantRoots = mutableListOf<AccessibilityNodeInfo>()
        var activePackage = eventPackage

        try {
            val root = rootInActiveWindow
            if (root != null) {
                val rootPkg = root.packageName?.toString() ?: eventPackage
                activePackage = rootPkg
                if (isRideAppPackage(rootPkg)) {
                    relevantRoots.add(root)
                }
            }
        } catch (e: Exception) {
            debugLog("ERRO lendo rootInActiveWindow [$source]: ${e.message}")
        }

        try {
            windows?.forEach { window ->
                window.root?.let { root ->
                    val rootPkg = root.packageName?.toString()
                    if (rootPkg != null && isRideAppPackage(rootPkg)) {
                        activePackage = rootPkg
                        relevantRoots.add(root)
                    }
                }
            }
        } catch (e: Exception) {
            debugLog("ERRO lendo windows [$source]: ${e.message}")
        }

        if (relevantRoots.isEmpty()) {
            val now = System.currentTimeMillis()
            if (now - lastIdleHeartbeatAt > 5_000) {
                lastIdleHeartbeatAt = now
                debugLog("OCIOSO [$source] | package=$activePackage | nenhum app de corrida visivel")
            }
            return
        }

        debugLog("APP DE CORRIDA NA TELA [$source] | package=$activePackage")

        val allText = mutableListOf<String>()
        relevantRoots.forEach { collectText(it, allText) }

        val fullText = allText
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" | ")

        debugLog(
            "CAPTURA [$source] | package=$activePackage | class=$eventClassName | type=$eventType | " +
                "textCount=${allText.size} | distinctTextCount=${allText.distinct().size}"
        )

        if (fullText.isBlank()) {
            debugLog("CAPTURA SEM TEXTO [$source] | package=$activePackage")
            return
        }

        AccessibilityDebugStore.saveCapture(
            context = this,
            packageName = activePackage,
            className = eventClassName,
            eventType = eventType,
            textCount = allText.distinct().size,
            text = fullText
        )

        debugLog("[$source] TEXTO BRUTO COLETADO (${allText.distinct().size} itens):\n$fullText")

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

        val tripData = extractTripData(fullText)

        if (tripData == null) {
            debugLog("[$source] NAO EXTRAIU CORRIDA DO TEXTO ACIMA")
            return
        }

        val key = "${tripData.value}-${tripData.distance}-${tripData.minutes}"

        if (key == lastProcessedText) return
        lastProcessedText = key

        debugLog(
            "[$source] EXTRAIU: valor=${tripData.value} " +
                "dist_total=${tripData.distance}km dist_ate_passageiro=${tripData.pickupDistanceKm ?: "NA"}km " +
                "dist_viagem=${tripData.tripDistanceKm}km min_total=${tripData.minutes} " +
                "min_ate_passageiro=${tripData.pickupMinutes ?: "NA"} min_viagem=${tripData.tripMinutes} " +
                "modalidade=${tripData.modality.ifBlank { "NA" }} destino=${tripData.dest.ifBlank { "NA" }}"
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
        val dest: String,
        val pickupDistanceKm: Double?,
        val tripDistanceKm: Double,
        val pickupMinutes: Int?,
        val tripMinutes: Int,
        val modality: String
    )

    private fun extractTripData(fullText: String): RawTripData? {
        return try {
            val diagnostic = UberOfferParser.diagnoseStructured(fullText)
            val offer = diagnostic.toOfferData()

            if (offer == null) {
                if (diagnostic.normalizedText != lastFailedParserText) {
                    lastFailedParserText = diagnostic.normalizedText
                    debugLog("PARSER FALHOU:\n${diagnostic.toLogString(includeText = true)}")
                }
                return null
            }

            lastFailedParserText = ""

            RawTripData(
                value = offer.value,
                distance = offer.totalDistanceKm,
                minutes = offer.totalMinutes,
                origin = offer.origin,
                dest = offer.destination,
                pickupDistanceKm = offer.pickupDistanceKm,
                tripDistanceKm = offer.tripDistanceKm,
                pickupMinutes = offer.pickupMinutes,
                tripMinutes = offer.tripMinutes,
                modality = offer.modality
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

            debugFile.parentFile?.mkdirs()
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
