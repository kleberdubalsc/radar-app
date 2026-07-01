package com.kleber.radar.util

import java.util.Locale

object UberOfferParser {

    data class OfferData(
        val value: Double,
        val pickupDistanceKm: Double?,
        val tripDistanceKm: Double,
        val totalDistanceKm: Double,
        val pickupMinutes: Int?,
        val tripMinutes: Int,
        val totalMinutes: Int,
        val origin: String,
        val destination: String,
        val modality: String
    )

    data class Diagnostic(
        val normalizedText: String,
        val valueCandidates: List<Double>,
        val value: Double?,
        val pickupDistanceKm: Double?,
        val tripDistanceKm: Double?,
        val totalDistanceKm: Double?,
        val pickupMinutes: Int?,
        val tripMinutes: Int?,
        val totalMinutes: Int?,
        val origin: String,
        val destination: String,
        val modality: String,
        val missingRequired: List<String>,
        val missingOptional: List<String>
    ) {
        val isRecognized: Boolean = missingRequired.isEmpty()

        fun toOfferData(): OfferData? {
            val parsedValue = value ?: return null
            val parsedTripDistance = tripDistanceKm ?: return null
            val parsedTotalDistance = totalDistanceKm ?: return null
            val parsedTripMinutes = tripMinutes ?: return null
            val parsedTotalMinutes = totalMinutes ?: return null

            return OfferData(
                value = parsedValue,
                pickupDistanceKm = pickupDistanceKm,
                tripDistanceKm = parsedTripDistance,
                totalDistanceKm = parsedTotalDistance,
                pickupMinutes = pickupMinutes,
                tripMinutes = parsedTripMinutes,
                totalMinutes = parsedTotalMinutes,
                origin = origin,
                destination = destination,
                modality = modality
            )
        }

        fun toLogString(includeText: Boolean = false): String {
            val status = if (isRecognized) "OK" else "FALHOU"
            val base = buildString {
                appendLine("PARSER $status")
                appendLine("valor=${formatMoney(value)} candidatos=${valueCandidates.joinToString { formatMoney(it) }}")
                appendLine("distancia_ate_passageiro_km=${formatNumber(pickupDistanceKm)}")
                appendLine("distancia_viagem_km=${formatNumber(tripDistanceKm)}")
                appendLine("distancia_total_km=${formatNumber(totalDistanceKm)}")
                appendLine("tempo_ate_passageiro_min=${formatInt(pickupMinutes)}")
                appendLine("tempo_viagem_min=${formatInt(tripMinutes)}")
                appendLine("tempo_total_min=${formatInt(totalMinutes)}")
                appendLine("origem=${origin.ifBlank { "NAO_ENCONTRADO" }}")
                appendLine("destino=${destination.ifBlank { "NAO_ENCONTRADO" }}")
                appendLine("modalidade=${modality.ifBlank { "NAO_ENCONTRADO" }}")
                appendLine("faltando_obrigatorio=${missingRequired.ifEmpty { listOf("nenhum") }.joinToString()}")
                appendLine("faltando_opcional=${missingOptional.ifEmpty { listOf("nenhum") }.joinToString()}")
            }.trimEnd()

            return if (includeText) "$base\ntexto_normalizado=$normalizedText" else base
        }
    }

    private data class SegmentMatch(
        val distanceKm: Double?,
        val minutes: Int?,
        val range: IntRange
    )

    fun parse(rawText: String): OfferData? = diagnoseStructured(rawText).toOfferData()

    fun diagnose(rawText: String): String = diagnoseStructured(rawText).toLogString(includeText = true)

    fun diagnoseStructured(rawText: String): Diagnostic {
        val text = normalize(rawText)
        val values = extractValues(text)
        val value = values.lastOrNull()
        val pickup = extractPickup(text)
        val trip = extractTrip(text)
        val tripDistance = trip?.distanceKm
        val tripMinutes = trip?.minutes
        val totalDistance = tripDistance?.let { it + (pickup?.distanceKm ?: 0.0) }
        val totalMinutes = tripMinutes?.let { it + (pickup?.minutes ?: 0) }
        val origin = extractOrigin(text, pickup, trip)
        val destination = extractDestination(text, trip)
        val modality = extractModality(text)

        val missingRequired = mutableListOf<String>()
        if (value == null) missingRequired.add("valor")
        if (tripDistance == null || tripDistance <= 0.0) missingRequired.add("distancia_viagem")
        if (tripMinutes == null || tripMinutes <= 0) missingRequired.add("tempo_viagem")

        val missingOptional = mutableListOf<String>()
        if (pickup?.distanceKm == null) missingOptional.add("distancia_ate_passageiro")
        if (pickup?.minutes == null) missingOptional.add("tempo_ate_passageiro")
        if (origin.isBlank()) missingOptional.add("origem")
        if (destination.isBlank()) missingOptional.add("destino")
        if (modality.isBlank()) missingOptional.add("modalidade")

        return Diagnostic(
            normalizedText = text,
            valueCandidates = values,
            value = value,
            pickupDistanceKm = pickup?.distanceKm,
            tripDistanceKm = tripDistance,
            totalDistanceKm = totalDistance,
            pickupMinutes = pickup?.minutes,
            tripMinutes = tripMinutes,
            totalMinutes = totalMinutes,
            origin = origin,
            destination = destination,
            modality = modality,
            missingRequired = missingRequired,
            missingOptional = missingOptional
        )
    }

    private fun normalize(rawText: String): String {
        return rawText
            .replace("|", " ")
            .replace("\n", " ")
            .replace("\r", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractValues(text: String): List<Double> {
        val valueRegex = Regex(
            "R\\$\\s*([0-9]{1,3}(?:\\.[0-9]{3})*,[0-9]{2}|[0-9]+[,.][0-9]{2}|[0-9]+)",
            RegexOption.IGNORE_CASE
        )

        return valueRegex.findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1)?.let(::parseMoney) }
            .filter { it > 0.0 }
            .toList()
    }

    private fun extractPickup(text: String): SegmentMatch? {
        val pickupPatterns = listOf(
            Regex(
                "(?:(\\d+)\\s*min(?:uto)?s?)?\\s*\\(?\\s*([0-9]+(?:[,.][0-9]+)?)\\s*(km|m)\\s*\\)?\\s*(?:de dist(?:a|â)ncia|ate o passageiro|até o passageiro|ate voce|até voce|ate você|até você)",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                "(?:dist(?:a|â)ncia|passageiro).*?([0-9]+(?:[,.][0-9]+)?)\\s*(km|m).*?(?:(\\d+)\\s*min(?:uto)?s?)?",
                RegexOption.IGNORE_CASE
            )
        )

        pickupPatterns.forEachIndexed { index, regex ->
            regex.find(text)?.let { match ->
                val minutes = when (index) {
                    0 -> parseInt(match.groupValues.getOrNull(1))
                    else -> parseInt(match.groupValues.getOrNull(3))
                }
                val number = when (index) {
                    0 -> match.groupValues.getOrNull(2)
                    else -> match.groupValues.getOrNull(1)
                }
                val unit = when (index) {
                    0 -> match.groupValues.getOrNull(3)
                    else -> match.groupValues.getOrNull(2)
                }

                return SegmentMatch(
                    distanceKm = parseDistance(number, unit),
                    minutes = minutes,
                    range = match.range
                )
            }
        }

        return null
    }

    private fun extractTrip(text: String): SegmentMatch? {
        val tripPatterns = listOf(
            Regex(
                "Viagem\\s+de\\s+(?:(\\d+)\\s*h(?:oras?)?\\s*(?:e\\s*)?)?(?:(\\d+)\\s*min(?:uto)?s?)\\s*\\(?\\s*([0-9]+(?:[,.][0-9]+)?)\\s*(km|m)\\s*\\)?",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                "Viagem\\s+de\\s+([0-9]+(?:[,.][0-9]+)?)\\s*(km|m)\\s*\\(?\\s*(?:(\\d+)\\s*h(?:oras?)?\\s*(?:e\\s*)?)?(?:(\\d+)\\s*min(?:uto)?s?)\\s*\\)?",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                "(?:(\\d+)\\s*h(?:oras?)?\\s*(?:e\\s*)?)?(?:(\\d+)\\s*min(?:uto)?s?)\\s+de\\s+viagem\\s*\\(?\\s*([0-9]+(?:[,.][0-9]+)?)\\s*(km|m)\\s*\\)?",
                RegexOption.IGNORE_CASE
            )
        )

        tripPatterns.forEachIndexed { index, regex ->
            regex.find(text)?.let { match ->
                val distance = when (index) {
                    1 -> parseDistance(match.groupValues.getOrNull(1), match.groupValues.getOrNull(2))
                    else -> parseDistance(match.groupValues.getOrNull(3), match.groupValues.getOrNull(4))
                }
                val minutes = when (index) {
                    1 -> parseDuration(match.groupValues.getOrNull(3), match.groupValues.getOrNull(4))
                    else -> parseDuration(match.groupValues.getOrNull(1), match.groupValues.getOrNull(2))
                }

                return SegmentMatch(
                    distanceKm = distance,
                    minutes = minutes,
                    range = match.range
                )
            }
        }

        return null
    }

    private fun extractOrigin(text: String, pickup: SegmentMatch?, trip: SegmentMatch?): String {
        if (pickup != null && trip != null && pickup.range.last < trip.range.first) {
            return cleanLocation(text.substring(pickup.range.last + 1, trip.range.first))
        }

        val regex = Regex(
            "(?:de dist(?:a|â)ncia|ate o passageiro|até o passageiro|ate voce|até voce|ate você|até você)\\s+(.+?)\\s+Viagem\\s+de",
            RegexOption.IGNORE_CASE
        )

        return cleanLocation(regex.find(text)?.groupValues?.getOrNull(1).orEmpty())
    }

    private fun extractDestination(text: String, trip: SegmentMatch?): String {
        trip ?: return ""
        val start = trip.range.last + 1
        if (start >= text.length) return ""

        val stopWords = listOf(" Selecionar", " Aceitar", " Recusar", " VIEW_ID:", " R$", " voltar")
        val end = stopWords
            .map { text.indexOf(it, startIndex = start, ignoreCase = true) }
            .filter { it >= 0 }
            .minOrNull()
            ?: text.length

        return cleanLocation(text.substring(start, end))
    }

    private fun extractModality(text: String): String {
        val modalities = listOf(
            "UberX Share",
            "Uber Comfort",
            "Uber Black",
            "Uber Flash Moto",
            "Uber Flash",
            "Uber Moto",
            "Uber Planet",
            "Uber Pet",
            "Uber Bag",
            "Prioridade",
            "Comfort",
            "Black",
            "Flash Moto",
            "Flash",
            "UberX",
            "Moto"
        )

        return modalities.firstOrNull { modality ->
            Regex("(?:^|\\s)${Regex.escape(modality)}(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(text)
        }.orEmpty()
    }

    private fun cleanLocation(value: String): String {
        if (value.isBlank()) return ""

        var clean = value
            .replace(Regex("VIEW_ID:[^\\s]+", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("R\\$\\s*[0-9.,]+", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\b(?:Selecionar|Aceitar|Recusar|UberX Share|Uber Comfort|Uber Black|Uber Flash Moto|Uber Flash|Uber Moto|Uber Planet|Uber Pet|Uber Bag|UberX|Comfort|Black|Flash Moto|Flash|Moto|Prioridade)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ',', '|')

        if (clean.length > 160) {
            clean = clean.take(160).trim()
        }

        return clean
    }

    private fun parseDuration(hoursRaw: String?, minutesRaw: String?): Int? {
        val hours = parseInt(hoursRaw) ?: 0
        val minutes = parseInt(minutesRaw) ?: 0
        val total = hours * 60 + minutes
        return total.takeIf { it > 0 }
    }

    private fun parseDistance(numberRaw: String?, unitRaw: String?): Double? {
        val number = parseDecimal(numberRaw) ?: return null
        return when (unitRaw?.lowercase(Locale.ROOT)) {
            "m" -> number / 1000.0
            else -> number
        }.takeIf { it > 0.0 }
    }

    private fun parseMoney(raw: String): Double? {
        val normalized = if (raw.contains(',')) {
            raw.replace(".", "").replace(",", ".")
        } else {
            raw
        }
        return normalized.toDoubleOrNull()
    }

    private fun parseDecimal(raw: String?): Double? {
        val value = raw?.takeIf { it.isNotBlank() } ?: return null
        val normalized = if (value.contains(',')) {
            value.replace(".", "").replace(",", ".")
        } else {
            value
        }
        return normalized.toDoubleOrNull()
    }

    private fun parseInt(raw: String?): Int? = raw?.takeIf { it.isNotBlank() }?.toIntOrNull()

    private fun formatMoney(value: Double?): String {
        return value?.let { "R$ %.2f".format(Locale.US, it) } ?: "NAO_ENCONTRADO"
    }

    private fun formatNumber(value: Double?): String {
        return value?.let { "%.2f".format(Locale.US, it) } ?: "NAO_ENCONTRADO"
    }

    private fun formatInt(value: Int?): String = value?.toString() ?: "NAO_ENCONTRADO"
}
