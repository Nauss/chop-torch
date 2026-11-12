// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import org.junit.Assert.*
import org.junit.Test

class TorchConfirmationTest {
    @Test fun confirmsRequestedOnTransition() {
        val confirmation=TorchConfirmation()
        confirmation.requested(true)
        assertTrue(confirmation.confirmed(false,true))
    }
    @Test fun confirmsRequestedOffTransition() {
        val confirmation=TorchConfirmation()
        confirmation.requested(false)
        assertTrue(confirmation.confirmed(true,false))
    }
    @Test fun initialAndExternalCallbacksStaySilent() {
        val confirmation=TorchConfirmation()
        assertFalse(confirmation.confirmed(null,false))
        assertFalse(confirmation.confirmed(false,true))
        assertFalse(confirmation.confirmed(true,false))
    }
    @Test fun duplicateConfirmationCannotVibrateAgain() {
        for (enabled in listOf(false,true)) {
            val confirmation=TorchConfirmation()
            confirmation.requested(enabled)
            assertTrue(confirmation.confirmed(!enabled,enabled))
            assertFalse(confirmation.confirmed(enabled,enabled))
            assertFalse(confirmation.confirmed(enabled,!enabled))
            assertFalse(confirmation.confirmed(!enabled,enabled))
        }
    }
    @Test fun duplicateBeforeSuccessPreservesConfirmation() {
        for (enabled in listOf(false,true)) {
            val confirmation=TorchConfirmation()
            confirmation.requested(enabled)
            repeat(2) { assertFalse(confirmation.confirmed(!enabled,!enabled)) }
            assertTrue(confirmation.confirmed(!enabled,enabled))
            assertFalse(confirmation.confirmed(enabled,enabled))
            assertFalse(confirmation.confirmed(!enabled,enabled))
        }
    }
    @Test fun duplicateThenCancellationKeepsLateTransitionSilent() {
        for (enabled in listOf(false,true)) {
            val confirmation=TorchConfirmation()
            confirmation.requested(enabled)
            assertFalse(confirmation.confirmed(!enabled,!enabled))
            confirmation.cancel()
            assertFalse(confirmation.confirmed(!enabled,enabled))
            assertFalse(confirmation.confirmed(enabled,enabled))
            confirmation.requested(!enabled)
            assertTrue(confirmation.confirmed(enabled,!enabled))
        }
    }
    @Test fun unknownPriorStateCannotConfirmAnAction() {
        for (enabled in listOf(false,true)) {
            val confirmation=TorchConfirmation()
            confirmation.requested(enabled)
            assertFalse(confirmation.confirmed(!enabled,!enabled))
            assertFalse(confirmation.confirmed(null,enabled))
            assertFalse(confirmation.confirmed(!enabled,enabled))
        }
    }
    @Test fun cancellationCoversFailureTimeoutUnavailableAndClose() {
        for (enabled in listOf(false,true)) {
            val confirmation=TorchConfirmation()
            confirmation.requested(enabled)
            confirmation.cancel()
            assertFalse(confirmation.confirmed(!enabled,enabled))
            confirmation.requested(!enabled)
            assertTrue(confirmation.confirmed(enabled,!enabled))
        }
    }
    @Test fun alternatingRequestsEachConfirmExactlyOnce() {
        val confirmation=TorchConfirmation()
        var current=false
        repeat(10) {
            confirmation.requested(!current)
            assertTrue(confirmation.confirmed(current,!current))
            current=!current
            assertFalse(confirmation.confirmed(current,current))
        }
    }
}
