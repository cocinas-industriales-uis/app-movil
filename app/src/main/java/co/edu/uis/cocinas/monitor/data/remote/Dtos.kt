package co.edu.uis.cocinas.monitor.data.remote

import android.util.Log
import co.edu.uis.cocinas.monitor.domain.Alarm
import co.edu.uis.cocinas.monitor.domain.AlarmCatalog
import co.edu.uis.cocinas.monitor.domain.AlarmStatus
import co.edu.uis.cocinas.monitor.domain.KitchenReading
import co.edu.uis.cocinas.monitor.domain.Role
import co.edu.uis.cocinas.monitor.domain.User
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/*
 * CONTRATO PROPUESTO con el backend (aún NO implementado en servidor/). Los nombres de los campos de
 * ReadingDto son los del payload MQTT real del firmware; el resto (auth, alarmas, stream) debe crearse en Django.
 */

@Serializable data class LoginRequest(val username: String, val password: String)
@Serializable data class UserDto(val id: String, val nombre: String, val username: String, val rol: String)
@Serializable data class LoginResponse(val access: String, val user: UserDto)
@Serializable data class AckRequest(val codigo: String)

@Serializable
data class ReadingDto(
    @SerialName("cocina_id") val cocinaId: String,
    val temperatura: Double,
    @SerialName("gas_raw") val gasRaw: Int,
    @SerialName("gas_pct") val gasPct: Int = 0,
    @SerialName("presion_raw") val presionRaw: Int,
    @SerialName("presion_pct") val presionPct: Int = 0,
    val llama: Boolean,
    @SerialName("delta_t") val deltaT: Double = 0.0,
    val prioridad: Int,
    @SerialName("v1_pct") val v1: Int = 0,
    @SerialName("v2_pct") val v2: Int = 0,
    @SerialName("v3_pct") val v3: Int = 0,
    val valvula: Boolean = false,
    val aspersor: Boolean = false,
    val confirmando: Boolean = false,
)

@Serializable
data class AlarmDto(
    val id: String,
    @SerialName("cocina_id") val cocinaId: String,
    val tipo: String,
    val variable: String,
    val sensor: String? = null,
    val valor: String,
    val umbral: String? = null,
    val prioridad: Int,
    val inicio: String,                  // ISO-8601
    val estado: String,                  // "ACTIVA" | "RECONOCIDA"
    @SerialName("reconocida_por") val reconocidaPor: String? = null,
    @SerialName("reconocida_en") val reconocidaEn: String? = null,
)

private const val TAG = "Dtos"

fun UserDto.toDomain() = User(
    id = id, name = nombre, username = username,
    role = if (rol.uppercase().startsWith("ADMIN")) Role.ADMIN else Role.OPERATOR,
)

/** Valida rangos: no se confía en que el servidor mande datos coherentes. Devuelve null si son inválidos. */
fun ReadingDto.toDomain(nowMs: Long): KitchenReading? {
    val ok = prioridad in 0..9 && temperatura in -60.0..400.0 &&
        gasRaw in 0..4095 && presionRaw in 0..4095 && cocinaId.isNotBlank()
    if (!ok) {
        Log.w(TAG, "Lectura descartada por datos fuera de rango (cocina=$cocinaId, prioridad=$prioridad)")
        return null
    }
    return KitchenReading(
        kitchenId = cocinaId, temperature = temperatura, gasRaw = gasRaw, gasPct = gasPct,
        pressureRaw = presionRaw, pressurePct = presionPct, flame = llama, deltaT = deltaT,
        priority = prioridad, fan1Pct = v1, fan2Pct = v2, fan3Pct = v3,
        valveClosed = valvula, sprinklerOn = aspersor, confirming = confirmando, receivedAtMs = nowMs,
    )
}

fun AlarmDto.toDomain(): Alarm = Alarm(
    id = id, kitchenId = cocinaId, type = tipo, variable = variable,
    sensor = sensor ?: AlarmCatalog.info(prioridad).sensor,
    value = valor, threshold = umbral, priority = prioridad,
    startedAtMs = runCatching { Instant.parse(inicio).toEpochMilli() }.getOrDefault(0L),
    status = if (estado.uppercase().startsWith("RECON")) AlarmStatus.ACKNOWLEDGED else AlarmStatus.ACTIVE,
    acknowledgedBy = reconocidaPor,
    acknowledgedAtMs = reconocidaEn?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
)
