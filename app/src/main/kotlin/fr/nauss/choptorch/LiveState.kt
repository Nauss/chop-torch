// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

/** Process-local observed state, never inferred from the saved activation preference. */
object LiveState {
    data class State(
        val running: Boolean = false,
        val message: String = "Arrêté — activation manuelle nécessaire",
        val sensor: String = "Non sélectionné",
        val wakeUp: Boolean = false,
        val torch: String = "Inconnue (aucun callback reçu)",
        val samples: Long = 0,
        val lastEventNs: Long = 0,
        val periodMs: Double = 0.0,
        val gestures: Long = 0
    )
    @Volatile var state = State()
        private set
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    @Synchronized fun update(change: (State) -> State) {
        state = change(state)
        main.post { listeners.forEach { it() } }
    }
    fun observe(listener: () -> Unit) { listeners.add(listener); listener() }
    fun remove(listener: () -> Unit) { listeners.remove(listener) }
}
