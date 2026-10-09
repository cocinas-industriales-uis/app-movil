package co.edu.uis.cocinas.monitor.data.mock

import co.edu.uis.cocinas.monitor.BuildConfig
import co.edu.uis.cocinas.monitor.domain.Alarm
import co.edu.uis.cocinas.monitor.domain.AlarmCatalog
import co.edu.uis.cocinas.monitor.domain.AlarmStatus
import co.edu.uis.cocinas.monitor.domain.ConnectionState
import co.edu.uis.cocinas.monitor.domain.InvalidCodeException
import co.edu.uis.cocinas.monitor.domain.KitchenReading
import co.edu.uis.cocinas.monitor.domain.MonitoringRepository
import co.edu.uis.cocinas.monitor.domain.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * SOLO DEBUG: simula 3 cocinas con los mismos campos/prioridades que el firmware real, para desarrollar y
 * probar la app sin backend. Código de reconocimiento simulado: ver MOCK_ACK_CODE (el real se valida en servidor).
 */
class MockMonitoringRepository(
    private val scope: CoroutineScope,
    private val currentUser: () -> User?,
) : MonitoringRepository {

    init { require(BuildConfig.DEBUG) { "El repositorio simulado solo existe en debug" } }

    companion object {
        const val MOCK_ACK_CODE = "123456"
        val KITCHENS = listOf("cocina_01", "cocina_02", "cocina_03")
    }

    private val _connection = MutableStateFlow(ConnectionState.RECONNECTING)
    private val _readings = MutableStateFlow<Map<String, KitchenReading>>(emptyMap())
    private val _alarms = MutableStateFlow(seedHistory())
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()
    override val readings: StateFlow<Map<String, KitchenReading>> = _readings.asStateFlow()
    override val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val scenario = ConcurrentHashMap<String, Int>()
    private val lastPriority = ConcurrentHashMap<String, Int>()
    @Volatile private var linkUp = true
    private var job: Job? = null
    private val rnd = Random(42)

    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            if (linkUp) _connection.value = ConnectionState.CONNECTED
            while (isActive) {
                if (linkUp) KITCHENS.forEach { publish(it) }
                delay(2_000)
            }
        }
    }

    override fun stop() {
        job?.cancel(); job = null
        _connection.value = ConnectionState.DISCONNECTED
    }

    /** Fuerza la prioridad de una cocina (0–9) para probar escenarios. */
    fun simulate(kitchenId: String, priority: Int) {
        scenario[kitchenId] = priority
        if (linkUp) publish(kitchenId)
    }

    /** Simula pérdida/recuperación de comunicación (las lecturas dejan de llegar). */
    fun setLinkUp(up: Boolean) {
        linkUp = up
        _connection.value = if (up) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
    }

    private fun publish(kitchenId: String) {
        val p = scenario[kitchenId] ?: 9
        val now = System.currentTimeMillis()
        val r = buildReading(kitchenId, p, now)
        _readings.update { it + (kitchenId to r) }
        // Una alarma nace al ENTRAR en una prioridad de riesgo, y se mantiene ACTIVA hasta reconocerla.
        val prev = lastPriority.put(kitchenId, p)
        if (prev != p) {
            AlarmCatalog.fromReading(r, UUID.randomUUID().toString(), now)?.let { a -> _alarms.update { listOf(a) + it } }
        }
    }

    private fun buildReading(k: String, p: Int, now: Long): KitchenReading {
        val noise = rnd.nextDouble()
        val temp = when (p) { 2 -> 95.0 + noise * 3; 3 -> 75.0 + noise * 5; 8 -> 40.0 + noise * 3; 0 -> 60.0 + noise * 4; else -> 26.0 + noise * 4 }
        val gas = when (p) { 0, 1 -> 2000 + (noise * 100).toInt(); 4, 5 -> 1000 + (noise * 100).toInt(); else -> 350 + (noise * 100).toInt() }
        val pres = when (p) { 5, 6 -> 2900 + (noise * 50).toInt(); else -> 1200 + (noise * 50).toInt() }
        val (v1, v2, v3) = when (p) {
            0, 2 -> Triple(0, 0, 0)
            1 -> Triple(100, 100, 100)
            3 -> Triple(0, 100, 0)
            4 -> Triple(60, 60, 0)
            5 -> Triple(0, 100, 100)
            else -> Triple(40, 40, 0)
        }
        return KitchenReading(
            kitchenId = k, temperature = temp, gasRaw = gas, gasPct = gas * 100 / 4095,
            pressureRaw = pres, pressurePct = pres * 100 / 4095,
            flame = p == 0 || p == 2 || p == 3 || p == 8, deltaT = if (p == 3) 2.5 else 0.2,
            priority = p, fan1Pct = v1, fan2Pct = v2, fan3Pct = v3,
            valveClosed = p in 0..3, sprinklerOn = p == 0 || p == 2 || p == 3,
            confirming = false, receivedAtMs = now,
        )
    }

    override suspend fun acknowledge(alarmId: String, code: String): Result<Alarm> {
        delay(300)
        if (code != MOCK_ACK_CODE) return Result.failure(InvalidCodeException())
        val user = currentUser() ?: return Result.failure(IllegalStateException("Sin sesión"))
        var result: Alarm? = null
        _alarms.update { list ->
            list.map { a ->
                if (a.id == alarmId && a.status == AlarmStatus.ACTIVE)
                    a.copy(status = AlarmStatus.ACKNOWLEDGED, acknowledgedBy = user.name, acknowledgedAtMs = System.currentTimeMillis())
                        .also { result = it }
                else a
            }
        }
        return result?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Alarma no encontrada o ya reconocida"))
    }

    private fun seedHistory(): List<Alarm> {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        fun old(id: String, k: String, p: Int, ago: Long, value: String): Alarm {
            val i = AlarmCatalog.info(p)
            return Alarm(id, k, i.type, i.variable, i.sensor, value, i.threshold, p, now - ago,
                AlarmStatus.ACKNOWLEDGED, "Operador Demo", now - ago + 95_000)
        }
        return listOf(
            old("h1", "cocina_01", 4, day, "Gas 24 % (raw 1010)"),
            old("h2", "cocina_02", 3, 2 * day, "76.0 °C, ΔT 2.4 °C/s"),
            old("h3", "cocina_01", 5, 4 * day, "Presión 70 %, gas 25 %"),
        )
    }
}
