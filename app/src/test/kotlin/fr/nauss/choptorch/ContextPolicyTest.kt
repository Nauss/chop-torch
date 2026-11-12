// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import org.junit.Assert.*
import org.junit.Test

class ContextPolicyTest {
    @Test fun proximityRequiresHardwareAndInitialReading() {
        for (range in doubleArrayOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0)) {
            val gate = ProximityGate(range)
            assertFalse(gate.available); gate.sample(1, 5.0); assertFalse(gate.allowsGesture)
        }
        val gate = ProximityGate(5.0)
        assertTrue(gate.available); assertEquals(ProximityGate.State.UNKNOWN, gate.state)
        assertFalse(gate.allowsGesture)
        assertTrue(gate.sample(1, 5.0)); assertTrue(gate.allowsGesture)
        assertFalse(gate.sample(2, 5.0))
        assertTrue(gate.sample(3, 0.0)); assertFalse(gate.allowsGesture)
        assertFalse(gate.sample(4, 0.0)); assertFalse(gate.allowsGesture)
    }

    @Test fun onChangeStatePersistsAndRangeIsDeviceSpecific() {
        for (range in doubleArrayOf(1.0, 5.0, 10.0, 100.0)) {
            val gate = ProximityGate(range)
            gate.sample(1, range); assertTrue(gate.allowsGesture)
            repeat(10_000) { assertTrue(gate.allowsGesture) }
            gate.sample(Long.MAX_VALUE, 0.0)
            repeat(10_000) { assertFalse(gate.allowsGesture) }
        }
        val gate = ProximityGate(100.0)
        gate.sample(1, 5.0); assertTrue(gate.allowsGesture)
    }

    @Test fun invalidOutOfOrderAndRestartFailClosed() {
        val gate = ProximityGate(5.0)
        gate.sample(10, 5.0)
        assertTrue(gate.sample(9, 5.0)); assertEquals(ProximityGate.State.UNKNOWN, gate.state)
        gate.sample(9, 5.0); assertFalse(gate.allowsGesture)
        gate.sample(11, 5.0); assertTrue(gate.allowsGesture)
        for (value in doubleArrayOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 5.01)) {
            gate.reset(); gate.sample(1, 5.0)
            gate.sample(2, value); assertFalse(gate.allowsGesture)
        }
        gate.reset(); gate.sample(0, 5.0); assertFalse(gate.allowsGesture)
        gate.sample(Long.MIN_VALUE, 5.0); assertFalse(gate.allowsGesture)
        gate.sample(1, 5.0); assertTrue(gate.allowsGesture)
        gate.reset(); assertFalse(gate.allowsGesture)
        gate.sample(1, 5.0); assertTrue(gate.allowsGesture)
    }

    @Test fun policyRefreshIsSampleDrivenButConsentIsEveryEvent() {
        var checks = 0; var consentChecks = 0; var consent = true; var reason = 0
        val gate = PolicyGate({ consentChecks++; consent }, { checks++; reason })
        repeat(100) { assertTrue(gate.allows(1_000_000_000L + it * 10_000_000L)) }
        assertEquals(1, checks); assertEquals(100, consentChecks)
        reason = 42
        assertFalse(gate.allows(2_000_000_000L)); assertEquals(2, checks)
        assertEquals(42, gate.blockedReason)
        reason = 0
        assertTrue(gate.allows(2_000_000_001L, force = true)); assertEquals(3, checks)
        consent = false
        assertFalse(gate.allows(2_000_000_002L)); assertEquals(3, checks)
    }

    @Test fun cachedPermissionCanNeverAuthorizeUncheckedCommand() {
        var checks = 0; var reason = 0; var consent = true
        val gate = PolicyGate({ consent }, { checks++; reason })
        assertTrue(gate.allows(1_000_000_000L))
        reason = 42
        assertTrue(gate.allows(1_020_000_000L)) // At most one-second observation delay.
        assertFalse(gate.allows(1_020_000_001L, force = true)) // Mandatory command path.
        assertEquals(2, checks)
        reason = 0; consent = false
        assertFalse(gate.allows(1_020_000_002L, force = true)); assertEquals(3, checks)
    }

    @Test fun policyClockReversalAndOverflowForceRefresh() {
        var checks = 0
        val gate = PolicyGate({ true }, { checks++; 0 })
        assertTrue(gate.allows(Long.MAX_VALUE - 10))
        assertTrue(gate.allows(Long.MAX_VALUE)); assertEquals(1, checks)
        assertTrue(gate.allows(1)); assertEquals(2, checks)
        assertTrue(gate.allows(1_000_000_001L)); assertEquals(3, checks)
        assertFalse(gate.allows(Long.MIN_VALUE)); assertEquals(3, checks)
    }
}
