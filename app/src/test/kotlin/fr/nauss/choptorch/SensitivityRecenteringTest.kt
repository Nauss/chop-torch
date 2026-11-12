// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Test

/** Calibration contracts only; these do not establish human effort or false-positive rates. */
class SensitivityRecenteringTest {
    private fun floor(level: Int): Double = DirectionalChopDetector(level).let { detector ->
        detector.javaClass.getDeclaredField("radialFloor").apply { isAccessible = true }.getDouble(detector)
    }

    @Test fun midpointPreservesTheAcceptedPreviousMinimum() {
        val acceptedOldMinimum = hypot(9.81, 18.0 * .8) - 9.81
        assertEquals(acceptedOldMinimum, floor(50), 1e-12)
    }

    @Test fun lowerHalfProvidesStrictlyFirmerSettings() {
        assertTrue(floor(0) > floor(50))
        for (level in 1..100) assertTrue("level=$level", floor(level - 1) > floor(level))
    }

    @Test fun upperEndpointRetainsPreviousMidpointNotItsExtreme() {
        val previousMidpoint = hypot(9.81, 18.0 / (1 + .1 * 50) * .8) - 9.81
        assertEquals(previousMidpoint, floor(100), 1e-12)
        assertTrue(floor(100) > .08)
    }
}
