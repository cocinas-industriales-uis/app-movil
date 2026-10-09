package co.edu.uis.cocinas.monitor.domain

import java.util.Locale

/** Un dato se considera desactualizado si pasa este tiempo sin llegar (el firmware envía cada 0.5–5 s). */
const val STALE_AFTER_MS = 15_000L

/**
 * Severidad derivada de la prioridad del firmware (P0–P9, ver esp32/cocina_esp32_1_.ino).
 * P7 (ventilación proporcional por temperatura 20–60 °C) es operación normal de una cocina, no advertencia.
 */
enum class Severity {
    NORMAL, WARNING, ELEVATED, EMERGENCY;

    companion object {
        fun fromPriority(priority: Int): Severity = when (priority) {
            0, 1, 2, 3 -> EMERGENCY
            4, 5 -> ELEVATED
            6, 8 -> WARNING
            else -> NORMAL
        }
    }
}

enum class ConnectionState { CONNECTED, RECONNECTING, DISCONNECTED }

enum class Role(val label: String) { ADMIN("Administrador"), OPERATOR("Operador") }

data class User(val id: String, val name: String, val username: String, val role: Role)

/** Lectura de una cocina. Campos = payload MQTT real del firmware (temperatura, gas_raw, presion_raw, llama, ...). */
data class KitchenReading(
    val kitchenId: String,
    val temperature: Double,
    val gasRaw: Int,
    val gasPct: Int,
    val pressureRaw: Int,
    val pressurePct: Int,
    val flame: Boolean,
    val deltaT: Double,
    val priority: Int,
    val fan1Pct: Int,
    val fan2Pct: Int,
    val fan3Pct: Int,
    val valveClosed: Boolean,
    val sprinklerOn: Boolean,
    val confirming: Boolean,
    val receivedAtMs: Long,
) {
    val severity: Severity get() = Severity.fromPriority(priority)
    fun ageMs(now: Long) = now - receivedAtMs
    fun isStale(now: Long) = ageMs(now) > STALE_AFTER_MS
}

enum class AlarmStatus { ACTIVE, ACKNOWLEDGED }

data class Alarm(
    val id: String,
    val kitchenId: String,
    val type: String,
    val variable: String,
    val sensor: String,
    val value: String,
    val threshold: String?,
    val priority: Int,
    val startedAtMs: Long,
    val status: AlarmStatus,
    val acknowledgedBy: String?,
    val acknowledgedAtMs: Long?,
) {
    val severity: Severity get() = Severity.fromPriority(priority)
    fun activeDurationMs(now: Long) = (acknowledgedAtMs ?: now) - startedAtMs
}

/** Textos y umbrales de cada prioridad, tomados de los #define del firmware. */
object AlarmCatalog {
    data class Info(val type: String, val variable: String, val sensor: String, val threshold: String)

    fun info(priority: Int): Info = when (priority) {
        0 -> Info("Gas y llama simultáneos", "Gas + llama", "Gas (ADC 35) + llama KY-026 (GPIO 14)", "Gas ≥ 20 % LEL (raw 1640) con llama")
        1 -> Info("Fuga de gas crítica", "Concentración de gas", "Gas (ADC 35)", "≥ 20 % LEL (raw 1640)")
        2 -> Info("Incendio severo", "Temperatura + llama", "NTC (ADC 34) + llama KY-026 (GPIO 14)", "> 90 °C con llama")
        3 -> Info("Incendio moderado", "Temperatura + llama", "NTC (ADC 34) + llama KY-026 (GPIO 14)", "≥ 60 °C o ΔT ≥ 2 °C/s, con llama")
        4 -> Info("Prealerta de gas", "Concentración de gas", "Gas (ADC 35)", "≥ 10 % LEL (raw 800)")
        5 -> Info("Presión alta con gas", "Presión + gas", "Presión (ADC 32) + gas (ADC 35)", "Presión ≥ 68 % y gas ≥ 10 % LEL")
        else -> Info("Condición de riesgo", "—", "—", "—")
    }

    private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)

    fun valueText(priority: Int, r: KitchenReading): String = when (priority) {
        0 -> "Gas ${r.gasPct} % (raw ${r.gasRaw}) + llama"
        1, 4 -> "Gas ${r.gasPct} % (raw ${r.gasRaw})"
        2, 3 -> "${f1(r.temperature)} °C, ΔT ${f1(r.deltaT)} °C/s"
        5 -> "Presión ${r.pressurePct} %, gas ${r.gasPct} %"
        else -> "—"
    }

    /** Solo P0–P5 generan alarma con reconocimiento; P6/P8 se muestran como advertencia de estado. */
    fun fromReading(r: KitchenReading, id: String, nowMs: Long): Alarm? {
        if (r.priority !in 0..5) return null
        val i = info(r.priority)
        return Alarm(
            id = id, kitchenId = r.kitchenId, type = i.type, variable = i.variable, sensor = i.sensor,
            value = valueText(r.priority, r), threshold = i.threshold, priority = r.priority,
            startedAtMs = nowMs, status = AlarmStatus.ACTIVE, acknowledgedBy = null, acknowledgedAtMs = null,
        )
    }
}

fun kitchenName(id: String): String =
    id.replace('_', ' ').replaceFirstChar { it.uppercase() }
