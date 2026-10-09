package co.edu.uis.cocinas.monitor.data.remote

import android.util.Log
import co.edu.uis.cocinas.monitor.domain.Alarm
import co.edu.uis.cocinas.monitor.domain.ConnectionState
import co.edu.uis.cocinas.monitor.domain.InvalidCodeException
import co.edu.uis.cocinas.monitor.domain.KitchenReading
import co.edu.uis.cocinas.monitor.domain.MonitoringRepository
import co.edu.uis.cocinas.monitor.util.Backoff
import co.edu.uis.cocinas.monitor.util.connectionStateFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import retrofit2.HttpException

/**
 * Consume la API del backend (no el broker MQTT directamente): snapshot REST + stream SSE `GET stream/`
 * con eventos `reading` (ReadingDto) y `alarm` (AlarmDto). Reconecta con backoff exponencial.
 * SIN PROBAR contra un servidor real: el endpoint de stream todavía no existe en el backend.
 */
class RemoteMonitoringRepository(
    private val api: ApiService,
    private val sseClient: OkHttpClient,      // readTimeout = 0 (conexión larga)
    private val baseUrl: String,
    private val scope: CoroutineScope,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : MonitoringRepository {

    private val _connection = MutableStateFlow(ConnectionState.RECONNECTING)
    private val _readings = MutableStateFlow<Map<String, KitchenReading>>(emptyMap())
    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()
    override val readings: StateFlow<Map<String, KitchenReading>> = _readings.asStateFlow()
    override val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val backoff = Backoff()
    private var job: Job? = null
    private var source: EventSource? = null

    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val closed = CompletableDeferred<Unit>()
                try {
                    loadSnapshot()
                    val req = Request.Builder().url("${baseUrl}stream/").header("Accept", "text/event-stream").build()
                    source = EventSources.createFactory(sseClient).newEventSource(req, object : EventSourceListener() {
                        override fun onOpen(eventSource: EventSource, response: Response) {
                            backoff.reset()
                            _connection.value = ConnectionState.CONNECTED
                            Log.i(TAG, "Conectado al stream")
                        }
                        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) =
                            handleEvent(type, data)
                        override fun onClosed(eventSource: EventSource) { closed.complete(Unit) }
                        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                            Log.w(TAG, "Stream caído: ${t?.javaClass?.simpleName ?: response?.code}")
                            closed.complete(Unit)
                        }
                    })
                    closed.await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Error de conexión: ${e.javaClass.simpleName}")
                }
                source?.cancel(); source = null
                val wait = backoff.nextDelayMs()
                _connection.value = connectionStateFor(backoff.failures)
                Log.i(TAG, "Reintentando en ${wait} ms (fallos consecutivos=${backoff.failures})")
                delay(wait)
            }
        }
    }

    override fun stop() {
        job?.cancel(); job = null
        source?.cancel(); source = null
        _connection.value = ConnectionState.DISCONNECTED
    }

    private suspend fun loadSnapshot() {
        val now = System.currentTimeMillis()
        _readings.value = api.latestReadings().mapNotNull { it.toDomain(now) }.associateBy { it.kitchenId }
        _alarms.value = api.alarms().map { it.toDomain() }.sortedByDescending { it.startedAtMs }
    }

    private fun handleEvent(type: String?, data: String) {
        try {
            when (type) {
                "reading" -> json.decodeFromString<ReadingDto>(data).toDomain(System.currentTimeMillis())
                    ?.let { r -> _readings.update { it + (r.kitchenId to r) } }
                "alarm" -> upsert(json.decodeFromString<AlarmDto>(data).toDomain())
                else -> Unit
            }
        } catch (e: Exception) {
            Log.w(TAG, "Evento inválido ($type): ${e.javaClass.simpleName}")
        }
    }

    private fun upsert(a: Alarm) = _alarms.update { list ->
        (list.filterNot { it.id == a.id } + a).sortedByDescending { it.startedAtMs }
    }

    override suspend fun acknowledge(alarmId: String, code: String): Result<Alarm> = try {
        val updated = api.acknowledge(alarmId, AckRequest(code)).toDomain()
        upsert(updated)
        Log.i(TAG, "Alarma $alarmId reconocida")
        Result.success(updated)
    } catch (e: HttpException) {
        Log.w(TAG, "Reconocimiento rechazado: HTTP ${e.code()}")
        Result.failure(if (e.code() == 400 || e.code() == 403) InvalidCodeException() else e)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object { const val TAG = "Monitoring" }
}
