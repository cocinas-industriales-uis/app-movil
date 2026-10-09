package co.edu.uis.cocinas.monitor.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Fmt {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dateF = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val timeF = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun date(ms: Long): String = dateF.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun time(ms: Long): String = timeF.format(Instant.ofEpochMilli(ms).atZone(zone))

    fun duration(ms: Long): String {
        val s = maxOf(0L, ms) / 1000
        return when {
            s < 60 -> "$s s"
            s < 3600 -> "${s / 60} min ${s % 60} s"
            else -> "${s / 3600} h ${(s % 3600) / 60} min"
        }
    }

    fun ago(ms: Long): String = "hace ${duration(ms)}"
}
