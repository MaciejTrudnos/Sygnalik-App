package com.maciejtrudnos.sygnalik

import com.maciejtrudnos.sygnalik.model.GraphHopperInstruction
import com.maciejtrudnos.sygnalik.model.GraphHopperPath

data class NavigationStep(
    val instructionText: String,
    val sign: Int,
    val distanceToManeuverMeters: Double,
    val remainingDistanceMeters: Double,
    val arrived: Boolean
)

class NavigationManager {

    private data class Maneuver(
        val lat: Double,
        val lon: Double,
        val instructionText: String,
        val sign: Int,
        val segmentAfterMeters: Double
    )

    private var maneuvers: List<Maneuver> = emptyList()
    private var destination: Maneuver? = null
    private var currentIndex = 0
    private var lastDistanceToManeuver: Double? = null

    var isActive: Boolean = false
        private set

    fun setRoute(path: GraphHopperPath) {
        clear()
        val points = path.points?.coordinates.orEmpty()
        val instructions = path.instructions
        if (instructions.isEmpty()) return
        maneuvers = instructions.dropLast(1).drop(1).mapNotNull { instruction ->
            maneuverPoint(points, instruction, intervalStart = true)?.let { (lat, lon) ->
                Maneuver(lat, lon, instruction.text, instruction.sign, instruction.distance)
            }
        }
        val last = instructions.last()
        destination = maneuverPoint(points, last, intervalStart = false)?.let { (lat, lon) ->
            Maneuver(lat, lon, last.text, last.sign, last.distance)
        }
        if (destination != null) {
            isActive = true
        }
    }

    fun clear() {
        maneuvers = emptyList()
        destination = null
        currentIndex = 0
        lastDistanceToManeuver = null
        isActive = false
    }

    fun onLocationUpdate(lat: Double, lon: Double): NavigationStep? {
        if (!isActive) return null
        val destination = this.destination ?: return null

        advancePastManeuvers(lat, lon)

        if (currentIndex < maneuvers.size) {
            val maneuver = maneuvers[currentIndex]
            val distance = distanceMeters(lat, lon, maneuver.lat, maneuver.lon)
            lastDistanceToManeuver = distance
            return NavigationStep(
                instructionText = maneuver.instructionText,
                sign = maneuver.sign,
                distanceToManeuverMeters = distance,
                remainingDistanceMeters = distance +
                    maneuvers.drop(currentIndex).sumOf { it.segmentAfterMeters } +
                    destination.segmentAfterMeters,
                arrived = false
            )
        }

        val distance = distanceMeters(lat, lon, destination.lat, destination.lon)
        if (distance <= MANEUVER_THRESHOLD_M) {
            clear()
            return NavigationStep(
                instructionText = destination.instructionText,
                sign = destination.sign,
                distanceToManeuverMeters = 0.0,
                remainingDistanceMeters = 0.0,
                arrived = true
            )
        }

        return NavigationStep(
            instructionText = destination.instructionText,
            sign = destination.sign,
            distanceToManeuverMeters = distance,
            remainingDistanceMeters = distance,
            arrived = false
        )
    }

    private fun advancePastManeuvers(lat: Double, lon: Double) {
        while (currentIndex < maneuvers.size) {
            val maneuver = maneuvers[currentIndex]
            val distance = distanceMeters(lat, lon, maneuver.lat, maneuver.lon)
            val last = lastDistanceToManeuver
            val passed = last != null &&
                last <= PASS_DETECTION_RADIUS_M &&
                distance > last + PASS_DETECTION_HYSTERESIS_M

            if (passed) {
                currentIndex++
                lastDistanceToManeuver = null
            } else {
                lastDistanceToManeuver = distance
                break
            }
        }
    }

    private fun maneuverPoint(
        points: List<List<Double>>,
        instruction: GraphHopperInstruction,
        intervalStart: Boolean
    ): Pair<Double, Double>? {
        val index = if (intervalStart) {
            instruction.interval.firstOrNull()
        } else {
            instruction.interval.getOrNull(1)
        }
        index ?: return null
        val coordinate = points.getOrNull(index) ?: return null
        val lon = coordinate.getOrNull(0) ?: return null
        val lat = coordinate.getOrNull(1) ?: return null
        return lat to lon
    }

    companion object {
        private const val MANEUVER_THRESHOLD_M = 40.0
        private const val PASS_DETECTION_RADIUS_M = 100.0
        private const val PASS_DETECTION_HYSTERESIS_M = 5.0
    }
}

fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusM = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return earthRadiusM * c
}

fun formatDistance(meters: Double): String =
    if (meters < 1000.0) {
        "${meters.toInt()} m"
    } else {
        "%.1f".format(java.util.Locale.US, meters / 1000.0) + " km"
    }

fun maneuverArrow(sign: Int): String? = when (sign) {
    -8, 8, -98 -> "U"
    -7, -3, -2, -1 -> "<--"
    -6, 1, 2, 3, 7 -> "-->"
    0 -> "^"
    6 -> "O"
    else -> null
}

fun toAsciiText(text: String): String {
    val transliterated = text.map { char ->
        when (char) {
            'ą' -> 'a'
            'ć' -> 'c'
            'ę' -> 'e'
            'ł' -> 'l'
            'ń' -> 'n'
            'ó' -> 'o'
            'ś' -> 's'
            'ź' -> 'z'
            'ż' -> 'z'
            'Ą' -> 'A'
            'Ć' -> 'C'
            'Ę' -> 'E'
            'Ł' -> 'L'
            'Ń' -> 'N'
            'Ó' -> 'O'
            'Ś' -> 'S'
            'Ź' -> 'Z'
            'Ż' -> 'Z'
            else -> char
        }
    }
    return String(transliterated.filter { it.code in 32..126 || it == '\n' }.toCharArray())
}