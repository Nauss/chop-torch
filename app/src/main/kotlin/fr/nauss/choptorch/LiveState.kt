// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

/** Process-local observed state, never inferred from the saved activation preference. */
object LiveState {
    data class State(
        val running: Boolean = false,
        val error: Int = 0,
        val torch: Int = R.string.torch_unknown
    )
    @Volatile var state = State()
        private set
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    @Synchronized fun update(change: (State) -> State) {
        val next = change(state)
        if (next == state) return
        state = next
        if (listeners.isNotEmpty()) main.post { listeners.forEach { it() } }
    }
    fun observe(listener: () -> Unit) { listeners.add(listener); listener() }
    fun remove(listener: () -> Unit) { listeners.remove(listener) }
}
