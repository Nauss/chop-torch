package fr.nauss.choptorch

import org.junit.Assert.*
import org.junit.Test

/** Synthetic time values: stale FIFO events must never toggle a lamp on pickup. */
class SampleFreshnessTest {
    @Test fun acceptsCurrentSampleButRejectsFutureAndDelayedData() {
        assertTrue(SampleFreshness.accepts(1_000_000_000L,1_010_000_000L))
        assertFalse(SampleFreshness.accepts(1_000_000_000L,2_000_000_000L))
        assertFalse(SampleFreshness.accepts(2_000_000_000L,1_000_000_000L))
        assertFalse(SampleFreshness.accepts(0L,1_000_000_000L))
    }
}
