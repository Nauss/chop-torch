// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

/** Permission is a product requirement: Android itself permits an FGS without notifications. */
object RecoveryPolicy {
    fun mayRecover(enabled: Boolean, notifications: Boolean, restricted: Boolean, userStopped: Boolean) =
        enabled && notifications && !restricted && !userStopped
}
