package com.maciejtrudnos.sygnalik

import com.maciejtrudnos.sygnalik.model.GraphHopperInstruction
import com.maciejtrudnos.sygnalik.model.GraphHopperPath

data class NavigationStep(
    val instructionText: String,
    val distanceToManeuverMeters: Double,
    val remainingDistanceMeters: Double,
    val arrived: Boolean
)

class NavigationManager {

    private data class Maneuver(
        val lat: Double,
        val lon: Double,
        val instructionText: String,
        val distanceMeters: Double
    )

    private var maneuvers: List<Maneuver> = emptyList()
    private var currentIndex = 0
    private var lastDistanceToManeuver: Double? = null

    var isActive: Boolean = false
        private set

    fun setRoute(path: GraphHopperPath) {
        clear()
        val points = path.points?.coordinates.orEmpty()
        maneuvers = path.instructions.mapNotNull { instruction ->
            maneuverPoint(points, instruction)?.let { (lat, lon) ->
                Maneuver(lat, lon, instruction.text, instruction.distance)
            }
        }
        if (maneuvers.isNotEmpty()) {
            isActive = true
        }
    }

    fun clear() {
        maneuvers = emptyList()
        currentIndex = 0
        lastDistanceToManeuver = null
        isActive = false
    }

    fun onLocationUpdate(lat: Double, lon: Double): NavigationStep? {
        if (!isActive) return null

        while (currentIndex < maneuvers.lastIndex) {
            val maneuver = maneuvers[currentIndex]
            val distance = distanceMeters(lat, lon, maneuver.lat, maneuver.lon)
            val last = lastDistanceToManeuver
            val passed = last != null &&
                last <= PASS_DETECTION_RADIUS_M &&
                distance > last + PASS_DETECTION_HYSTERESIS_M

            if (distance <= MANEUVER_THRESHOLD_M || passed) {
                currentIndex++
                lastDistanceToManeuver = null
            } else {
                lastDistanceToManeuver = distance
                break
            }
        }

        val maneuver = maneuvers[currentIndex]
        val distance = distanceMeters(lat, lon, maneuver.lat, maneuver.lon)

        if (currentIndex == maneuvers.lastIndex && distance <= MANEUVER_THRESHOLD_M) {
            clear()
            return NavigationStep(
                instructionText = maneuver.instructionText,
                distanceToManeuverMeters = 0.0,
                remainingDistanceMeters = 0.0,
                arrived = true
            )
        }

        lastDistanceToManeuver = distance
        return NavigationStep(
            instructionText = maneuver.instructionText,
            distanceToManeuverMeters = distance,
            remainingDistanceMeters = remainingDistance(distance),
            arrived = false
        )
    }

    private fun remainingDistance(distanceToManeuver: Double): Double =
        distanceToManeuver + maneuvers.drop(currentIndex + 1).sumOf { it.distanceMeters }

    private fun maneuverPoint(
        points: List<List<Double>>,
        instruction: GraphHopperInstruction
    ): Pair<Double, Double>? {
        val index = instruction.interval.getOrNull(1) ?: return null
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