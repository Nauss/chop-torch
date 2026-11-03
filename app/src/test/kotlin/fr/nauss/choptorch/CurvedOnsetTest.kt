package fr.nauss.choptorch

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.*

/** SYNTHETIC acceleration curves, not personal capture samples. */
class CurvedOnsetTest {
    private fun pair(periodMs: Int, curved: Boolean, perpendicular: Boolean = false,
        oppositeBends: Boolean = false, sensitivity: Int = 100, amplitude: Double = 6.0,
        stepMs: Int = 8, axis: Int = 0): Int {
        val detector = ChopDetector(sensitivity)
        var t = 1_000_000_000L
        var hits = 0
        fun sample(x: Double, y: Double, z: Double) {
            val v = doubleArrayOf(x, y, z)
            if (detector.sample(t, v[axis % 3], v[(axis + 1) % 3], v[(axis + 2) % 3] + 9.81)) hits++
            t += stepMs * 1_000_000L
        }
        repeat(100) { sample(0.0, 0.0, 0.0) }
        repeat(2) { stroke ->
            for (ms in 0 until periodMs step stepMs) {
                val phase = ms.toDouble() / periodMs
                val thrust = amplitude * sin(2 * PI * phase)
                // Brief curved onset, then sustained common travel direction.
                val angle = if (oppositeBends && phase < 0.5)
                    (if (stroke == 0) 1 else -1) * (PI * 35 / 180) *
                        minOf((phase - 0.1) / 0.1, (0.5 - phase) / 0.1).coerceIn(0.0, 1.0)
                    else if (curved && stroke == 1 && phase < 0.25)
                        (PI / 3) * (1 - phase / 0.25) else 0.0
                val x = thrust * cos(angle)
                val z = thrust * sin(angle)
                sample(if (perpendicular && stroke == 1) 0.0 else x,
                    if (perpendicular && stroke == 1) x else 0.0, z)
            }
        }
        repeat(100) { sample(0.0, 0.0, 0.0) }
        return hits
    }
    @Test fun normalAndRapidCurvedOnsetsUseSustainedDirection() {
        for (period in listOf(200, 280)) {
            assertEquals("straight period=$period", 1, pair(period, false))
            assertEquals("curved period=$period", 1, pair(period, true))
        }
    }
    @Test fun sustainedPerpendicularTravelStillRejects() {
        for (period in listOf(200, 280)) assertEquals(0, pair(period, true, true))
    }
    @Test fun coherentOnsetsSurviveOppositeModerateBends() {
        for (period in listOf(200, 280)) assertEquals("period=$period", 1,
            pair(period, false, oppositeBends = true))
    }
    @Test fun curvedPairsCoverCadenceAxesAndAmplitudeAtUnchangedSetting() {
        for ((s, amplitude) in listOf(100 to 6.0, 50 to 24.0, 0 to 24.0))
            for (period in listOf(200, 280)) for (step in listOf(7, 8, 10))
                for (axis in 0..2) for (curved in listOf(false, true))
                    assertEquals("s=$s period=$period step=$step axis=$axis curved=$curved", 1,
                        pair(period, curved, sensitivity=s, amplitude=amplitude, stepMs=step, axis=axis))
    }
    @Test fun onsetOfOneStrokeCannotMatchTravelOfTheOther() {
        for (s in listOf(0, 50, 100)) {
            val detector = ChopDetector(s)
            var t = 1_000_000_000L
            var hits = 0
            fun run(ms: Int, amplitude: Double, degrees: Double) {
                repeat(ms / 8) {
                    val angle = degrees * PI / 180
                    if (detector.sample(t, amplitude * cos(angle), amplitude * sin(angle), 9.81)) hits++
                    t += 8_000_000L
                }
            }
            run(800, 0.0, 0.0)
            // First travel ~X, second onset X: only a forbidden CROSS-match aligns.
            run(16, 24.0, -50.0); run(80, 24.0, 0.0)
            run(80, -24.0, 0.0); run(24, 0.0, 0.0)
            run(16, 24.0, 0.0); run(80, 24.0, 50.0)
            run(80, -24.0, 50.0); run(800, 0.0, 0.0)
            assertEquals("cross-witness s=$s", 0, hits)
        }
    }
}
