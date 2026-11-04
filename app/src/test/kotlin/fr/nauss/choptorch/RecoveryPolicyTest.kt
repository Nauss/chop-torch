// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import org.junit.Assert.*
import org.junit.Test

class RecoveryPolicyTest {
    @Test fun enabledValidAppRecovers() {
        assertTrue(RecoveryPolicy.mayRecover(true, true, false, false))
    }
    @Test fun deniedNotificationsPreventRecovery() {
        assertFalse(RecoveryPolicy.mayRecover(true, false, false, false))
    }
    @Test fun restrictedAppDoesNotAttemptBackgroundStart() {
        assertFalse(RecoveryPolicy.mayRecover(true, true, true, false))
    }
    @Test fun userStopOverridesSavedIntent() {
        assertFalse(RecoveryPolicy.mayRecover(true, true, false, true))
    }
    @Test fun freshOrDisabledAppNeverRecovers() {
        assertFalse(RecoveryPolicy.mayRecover(false, true, false, false))
    }
}
