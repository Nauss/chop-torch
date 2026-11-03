package fr.nauss.choptorch

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticSampleTest {
    @Test fun exportPreservesBothClockReadingsAndActualAdmission() {
        val sample = DiagnosticSample(1_008_400_000L, 1_310_000_000L, 1f, 2f, 3f, false, 100)
        assertEquals(listOf(8.4, 1.0, 2.0, 3.0, 310.0, false, 100), sample.columns(1_000_000_000L))
    }
    @Test fun outOfOrderCallbackDoesNotRewriteTheSensorTimestamp() {
        val sample = DiagnosticSample(990_000_000L, 1_010_000_000L, 1f, 2f, 3f, true, 50)
        assertEquals(-10.0, sample.columns(1_000_000_000L)[0])
        assertEquals(10.0, sample.columns(1_000_000_000L)[4])
    }
}
