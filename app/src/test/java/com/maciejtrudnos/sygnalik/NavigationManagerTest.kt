package com.maciejtrudnos.sygnalik

import com.maciejtrudnos.sygnalik.model.GraphHopperGeoJson
import com.maciejtrudnos.sygnalik.model.GraphHopperInstruction
import com.maciejtrudnos.sygnalik.model.GraphHopperPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationManagerTest {

    private fun sampleRoute() = GraphHopperPath(
        distance = 333.0,
        points = GraphHopperGeoJson(
            type = "LineString",
            coordinates = listOf(
                listOf(0.000, 0.000),
                listOf(0.001, 0.000),
                listOf(0.002, 0.000),
                listOf(0.003, 0.000)
            )
        ),
        instructions = listOf(
            GraphHopperInstruction(distance = 111.0, sign = 2, interval = listOf(0, 1), text = "Prosto", time = 10),
            GraphHopperInstruction(distance = 111.0, sign = 2, interval = listOf(1, 2), text = "Skręć w prawo", time = 10),
            GraphHopperInstruction(distance = 111.0, sign = 4, interval = listOf(2, 3), text = "Koniec trasy", time = 10)
        )
    )

    @Test
    fun distanceMeters_computesGreatCircleDistance() {
        val d = distanceMeters(0.0, 0.0, 0.0, 0.001)
        assertEquals(111.19, d, 1.0)
    }

    @Test
    fun returnsFirstInstruction_withDistanceToManeuver() {
        val manager = NavigationManager()
        manager.setRoute(sampleRoute())

        val step = manager.onLocationUpdate(0.0, 0.0)!!

        assertEquals("Prosto", step.instructionText)
        assertEquals(111.19, step.distanceToManeuverMeters, 1.0)
        assertEquals(333.58, step.remainingDistanceMeters, 2.0)
        assertFalse(step.arrived)
    }

    @Test
    fun advancesToNextInstruction_withinThreshold() {
        val manager = NavigationManager()
        manager.setRoute(sampleRoute())
        manager.onLocationUpdate(0.0, 0.0)

        val step = manager.onLocationUpdate(0.0, 0.0008)!!

        assertEquals("Skręć w prawo", step.instructionText)
        assertFalse(manager.isActive.not())
    }

    @Test
    fun advancesAfterPassing_maneuverPoint() {
        val manager = NavigationManager()
        manager.setRoute(sampleRoute())
        manager.onLocationUpdate(0.0, 0.0)
        manager.onLocationUpdate(0.0, 0.0008)
        manager.onLocationUpdate(0.0, 0.0015)

        val step = manager.onLocationUpdate(0.0, 0.0026)!!

        assertEquals("Koniec trasy", step.instructionText)
    }

    @Test
    fun arrivesAtFinalManeuver_andDeactivates() {
        val manager = NavigationManager()
        manager.setRoute(sampleRoute())
        manager.onLocationUpdate(0.0, 0.0)
        manager.onLocationUpdate(0.0, 0.0008)
        manager.onLocationUpdate(0.0, 0.0018)

        val step = manager.onLocationUpdate(0.0, 0.003)!!

        assertTrue(step.arrived)
        assertFalse(manager.isActive)
        assertNull(manager.onLocationUpdate(0.0, 0.0))
    }

    @Test
    fun returnsNull_whenRouteNotSet() {
        val manager = NavigationManager()

        assertNull(manager.onLocationUpdate(0.0, 0.0))
    }

    @Test
    fun staysInactive_whenRouteHasNoValidInstructions() {
        val manager = NavigationManager()
        manager.setRoute(GraphHopperPath(distance = 0.0))

        assertFalse(manager.isActive)
    }

    @Test
    fun formatDistance_formatsMetersAndKilometers() {
        assertEquals("300 m", formatDistance(300.4))
        assertEquals("4.2 km", formatDistance(4212.0))
    }
}