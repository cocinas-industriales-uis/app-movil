package co.edu.uis.cocinas.monitor.util

import co.edu.uis.cocinas.monitor.domain.ConnectionState

/** Espera exponencial 1 s, 2 s, 4 s … hasta 30 s, para no saturar el backend al reconectar. */
class Backoff(private val baseMs: Long = 1_000, private val maxMs: Long = 30_000) {
    var failures: Int = 0
        private set

    fun nextDelayMs(): Long {
        val d = minOf(maxMs, baseMs shl minOf(failures, 20))
        failures++
        return d
    }

    fun reset() { failures = 0 }
}

fun connectionStateFor(consecutiveFailures: Int): ConnectionState = when {
    consecutiveFailures <= 0 -> ConnectionState.CONNECTED
    consecutiveFailures < 3 -> ConnectionState.RECONNECTING
    else -> ConnectionState.DISCONNECTED
}
