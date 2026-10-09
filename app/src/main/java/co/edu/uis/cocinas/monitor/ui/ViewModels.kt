package co.edu.uis.cocinas.monitor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.uis.cocinas.monitor.CocinasApp
import co.edu.uis.cocinas.monitor.domain.Alarm
import co.edu.uis.cocinas.monitor.domain.AlarmStatus
import co.edu.uis.cocinas.monitor.domain.AuthRepository
import co.edu.uis.cocinas.monitor.domain.ConnectionState
import co.edu.uis.cocinas.monitor.domain.InvalidCodeException
import co.edu.uis.cocinas.monitor.domain.KitchenReading
import co.edu.uis.cocinas.monitor.domain.MonitoringRepository
import co.edu.uis.cocinas.monitor.domain.Severity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

// ───────────────────────── Auth ─────────────────────────

data class LoginUiState(val loading: Boolean = false, val error: String? = null)

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {
    val user = auth.user
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _state.value = LoginUiState(error = "Ingresa usuario y contraseña")
            return
        }
        viewModelScope.launch {
            _state.value = LoginUiState(loading = true)
            auth.login(username.trim(), password)
                .onSuccess { _state.value = LoginUiState() }
                .onFailure { _state.value = LoginUiState(error = friendly(it)) }
        }
    }

    fun logout() { viewModelScope.launch { auth.logout() } }

    private fun friendly(t: Throwable): String = when {
        t is HttpException && (t.code() == 400 || t.code() == 401) -> "Usuario o contraseña incorrectos"
        t is IOException -> "No se pudo conectar con el servidor"
        t is IllegalArgumentException -> "Usuario o contraseña incorrectos"
        else -> "No se pudo iniciar sesión"
    }
}

// ───────────────────────── Dashboard ─────────────────────────

data class KitchenUi(val reading: KitchenReading, val stale: Boolean, val ageMs: Long)

data class DashboardUiState(
    val overall: Severity? = null,            // null = sin datos frescos
    val connection: ConnectionState = ConnectionState.RECONNECTING,
    val lastUpdateAgoMs: Long? = null,
    val kitchens: List<KitchenUi> = emptyList(),
    val activeAlarms: Int = 0,
    val reporting: Int = 0,
)

class DashboardViewModel(monitoring: MonitoringRepository) : ViewModel() {
    private val ticker = flow { while (true) { emit(System.currentTimeMillis()); delay(1_000) } }

    val state: StateFlow<DashboardUiState> = combine(
        monitoring.readings, monitoring.connection, monitoring.alarms, ticker
    ) { readings, conn, alarms, now ->
        val kitchens = readings.values.sortedBy { it.kitchenId }.map { KitchenUi(it, it.isStale(now), it.ageMs(now)) }
        val fresh = kitchens.filterNot { it.stale }
        val active = alarms.filter { it.status == AlarmStatus.ACTIVE }
        // Una alarma sin reconocer mantiene el estado general en riesgo aunque la condición ya haya pasado.
        val overall = listOfNotNull(
            fresh.maxOfOrNull { it.reading.severity },
            active.maxOfOrNull { it.severity },
        ).maxOrNull()
        DashboardUiState(
            overall = overall,
            connection = conn,
            lastUpdateAgoMs = readings.values.maxOfOrNull { it.receivedAtMs }?.let { now - it },
            kitchens = kitchens,
            activeAlarms = active.size,
            reporting = fresh.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}

// ───────────────────────── Historial ─────────────────────────

class HistoryViewModel(monitoring: MonitoringRepository) : ViewModel() {
    val alarms: StateFlow<List<Alarm>> = monitoring.alarms
        .map { l -> l.sortedByDescending { it.startedAtMs } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

// ───────────────────────── Reconocer alarma ─────────────────────────

data class AckUiState(val busy: Boolean = false, val message: String? = null, val success: Boolean = false)

class AckViewModel(private val monitoring: MonitoringRepository) : ViewModel() {
    private val _state = MutableStateFlow(AckUiState())
    val state: StateFlow<AckUiState> = _state.asStateFlow()

    private var failures = 0
    private var lockedUntilMs = 0L

    /** El bloqueo local es solo de usabilidad; el límite real de intentos debe aplicarlo el servidor. */
    fun acknowledge(alarmId: String, code: String, nowMs: Long = System.currentTimeMillis()) {
        if (nowMs < lockedUntilMs) {
            _state.value = AckUiState(message = "Demasiados intentos. Espera unos segundos. La alarma continúa activa.")
            return
        }
        viewModelScope.launch {
            _state.value = AckUiState(busy = true)
            monitoring.acknowledge(alarmId, code)
                .onSuccess {
                    failures = 0
                    _state.value = AckUiState(message = "Alarma reconocida correctamente.", success = true)
                }
                .onFailure {
                    if (it is InvalidCodeException) {
                        failures++
                        if (failures >= MAX_FAILURES) { lockedUntilMs = nowMs + LOCK_MS; failures = 0 }
                        _state.value = AckUiState(message = "Código incorrecto. La alarma continúa activa.")
                    } else {
                        _state.value = AckUiState(message = "No se pudo contactar al sistema. La alarma continúa activa.")
                    }
                }
        }
    }

    fun consume() { _state.value = AckUiState() }

    companion object {
        const val MAX_FAILURES = 5
        const val LOCK_MS = 30_000L
    }
}

// ───────────────────────── Factory ─────────────────────────

object VmFactory {
    val factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { AuthViewModel(app().container.authRepository) }
        initializer { DashboardViewModel(app().container.monitoring) }
        initializer { HistoryViewModel(app().container.monitoring) }
        initializer { AckViewModel(app().container.monitoring) }
    }

    private fun androidx.lifecycle.viewmodel.CreationExtras.app(): CocinasApp =
        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CocinasApp
}
