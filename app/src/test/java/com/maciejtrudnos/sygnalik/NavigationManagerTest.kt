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
            GraphHopperInstruction(distance = 111.0, sign = 0, interval = listOf(0, 1), text = "Prosto", time = 10),
            GraphHopperInstruction(distance = 111.0, sign = 2, interval = listOf(1, 2), text = "Skręć w prawo", time = 10),
            GraphHopperInstruction(distance = 111.0, sign = 4, interval = listOf(2, 3), text = "Koniec trasy", time = 10)
        )
    )

    private fun twoTurnRoute() = GraphHopperPath(
        distance = 444.0,
        points = GraphHopperGeoJson(
            type = "LineString",
            coordinates = listOf(
                listOf(0.000, 0.000),
                listOf(0.001, 0.000),
                listOf(0.002, 0.000),
                listOf(0.003, 0.000),
                listOf(0.004, 0.000)
            )
        ),
        instructions = listOf(
            GraphHopperInstruction(distance = 111.0, sign = 0, interval = listOf(0, 1), text = "Prosto", time = 10),
            GraphHopperInstruction(distance = 111.0, sign = 2, interval = listOf(1, 2), text = "Skręć w prawo", time = 10),
            GraphHopperInstruction(distance = 111.0, sign = -2, interval = listOf(2, 3), text = "Skręć w lewo", time = 10),
            GraphHopperInstruction(distance = 111.0, sign = 4, interval = listOf(3, 4), text = "Koniec trasy", time = 10)
        )
    )

    @Test
    fun distanceMeters_computesGreatCircleDistance() {
        val d = distanceMeters(0.0, 0.0, 0.0, 0.001)
        assertEquals(111.19, d, 1.0)
    }

    @Test
    fun showsNextManeuver_fromRouteStart() {
        val manager = NavigationManager()
        manager.setRoute(sampleRoute())

        val step = manager.onLocationUpdate(0.0, 0.0)!!

        assertEquals("Skręć w prawo", step.instructionText)
        assertEquals(2, step.sign)
        assertEquals(111.19, step.distanceToManeuverMeters, 1.0)
        assertEquals(333.19, step.remainingDistanceMeters, 2.0)
        assertFalse(step.arrived)
    }

    @Test
    fun keepsCurrentManeuver_untilManeuverPassed() {
        val manager = NavigationManager()
        manager.setRoute(sampleRoute())
        manager.onLocationUpdate(0.0, 0.0)

        val step = manager.onLocationUpdate(0.0, 0.0008)!!

        assertEquals("Skręć w prawo", step.instructionText)
        assertEquals(2, step.sign)
        assertEquals(22.2, step.distanceToManeuverMeters, 1.0)
        assertFalse(step.arrived)
    }

    @Test
    fun updatesToNextManeuver_afterPassingManeuver() {
        val manager = NavigationManager()
        manager.setRoute(sampleRoute())
        manager.onLocationUpdate(0.0, 0.0)
        manager.onLocationUpdate(0.0, 0.0008)

        val step = manager.onLocationUpdate(0.0, 0.0015)!!

        assertEquals("Koniec trasy", step.instructionText)
        assertEquals(4, step.sign)
        assertEquals(166.8, step.distanceToManeuverMeters, 2.0)
        assertEquals(166.8, step.remainingDistanceMeters, 2.0)
        assertFalse(step.arrived)
    }

    @Test
    fun arrivesAtDestination_andDeactivates() {
        val manager = NavigationManager()
        manager.setRoute(sampleRoute())
        manager.onLocationUpdate(0.0, 0.0)
        manager.onLocationUpdate(0.0, 0.0008)
        manager.onLocationUpdate(0.0, 0.0015)

        val step = manager.onLocationUpdate(0.0, 0.003)!!

        assertTrue(step.arrived)
        assertEquals(4, step.sign)
        assertEquals(0.0, step.distanceToManeuverMeters, 0.0)
        assertFalse(manager.isActive)
        assertNull(manager.onLocationUpdate(0.0, 0.0))
    }

    @Test
    fun walksThroughMultipleManeuvers_updatingAfterEach() {
        val manager = NavigationManager()
        manager.setRoute(twoTurnRoute())

        val first = manager.onLocationUpdate(0.0, 0.0)!!
        assertEquals("Skręć w prawo", first.instructionText)
        assertEquals(2, first.sign)
        assertEquals(444.19, first.remainingDistanceMeters, 2.0)

        manager.onLocationUpdate(0.0, 0.0008)

        val afterRightTurn = manager.onLocationUpdate(0.0, 0.0015)!!
        assertEquals("Skręć w lewo", afterRightTurn.instructionText)
        assertEquals(-2, afterRightTurn.sign)
        assertEquals(55.6, afterRightTurn.distanceToManeuverMeters, 1.0)
        assertEquals(277.6, afterRightTurn.remainingDistanceMeters, 2.0)

        manager.onLocationUpdate(0.0, 0.0026)

        val afterLeftTurn = manager.onLocationUpdate(0.0, 0.0035)!!
        assertEquals("Koniec trasy", afterLeftTurn.instructionText)
        assertEquals(4, afterLeftTurn.sign)
        assertEquals(55.6, afterLeftTurn.distanceToManeuverMeters, 1.0)
        assertEquals(55.6, afterLeftTurn.remainingDistanceMeters, 2.0)

        val arrived = manager.onLocationUpdate(0.0, 0.004)!!
        assertTrue(arrived.arrived)
        assertFalse(manager.isActive)
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

    @Test
    fun toAsciiText_transliteratesPolishLetters() {
        assertEquals("Zawroc", toAsciiText("Zawróć"))
        assertEquals("Dotarles do celu", toAsciiText("Dotarłeś do celu"))
        assertEquals("Ostro w lewo", toAsciiText("Ostro w lewo"))
        assertEquals("Skrec skrec SKREC", toAsciiText("Skręć skręć SKRĘĆ"))
        assertEquals("ZOLC zolc", toAsciiText("ŻÓŁĆ żółć"))
    }

    @Test
    fun toAsciiText_preservesNewlines() {
        assertEquals(
            "Skrec w prawo\nZa 200 m\nDo celu: 1.2 km",
            toAsciiText("Skręć w prawo\nZa 200 m\nDo celu: 1.2 km")
        )
    }

    @Test
    fun toAsciiText_dropsNonAsciiCharacters() {
        assertEquals("X", toAsciiText("\u201EX\u201D"))
        assertEquals("Prosto  W lewo", toAsciiText("Prosto \u2013 W lewo"))
    }

    @Test
    fun maneuverArrow_mapsGraphHopperSigns() {
        assertEquals("U", maneuverArrow(-8))
        assertEquals("U", maneuverArrow(8))
        assertEquals("U", maneuverArrow(-98))
        assertEquals("<--", maneuverArrow(-7))
        assertEquals("<--", maneuverArrow(-3))
        assertEquals("<--", maneuverArrow(-2))
        assertEquals("<--", maneuverArrow(-1))
        assertEquals("-->", maneuverArrow(-6))
        assertEquals("-->", maneuverArrow(1))
        assertEquals("-->", maneuverArrow(2))
        assertEquals("-->", maneuverArrow(3))
        assertEquals("-->", maneuverArrow(7))
        assertEquals("^", maneuverArrow(0))
        assertEquals("O", maneuverArrow(6))
        assertNull(maneuverArrow(-99))
        assertNull(maneuverArrow(4))
        assertNull(maneuverArrow(5))
        assertNull(maneuverArrow(9))
        assertNull(maneuverArrow(101))
    }

    @Test
    fun toAsciiText_preservesAsciiArrows() {
        assertEquals("-->\nSkrec w prawo", toAsciiText("-->\nSkręć w prawo"))
        assertEquals("^ <-- U O", toAsciiText("^ <-- U O"))
    }
}