package co.edu.uis.cocinas.monitor.domain

import kotlinx.coroutines.flow.StateFlow

class InvalidCodeException : Exception("Código incorrecto")

interface AuthRepository {
    val user: StateFlow<User?>
    suspend fun login(username: String, password: String): Result<User>
    suspend fun logout()
}

interface MonitoringRepository {
    val connection: StateFlow<ConnectionState>
    /** Última lectura por cocina. */
    val readings: StateFlow<Map<String, KitchenReading>>
    /** Alarmas activas e historial, más recientes primero. */
    val alarms: StateFlow<List<Alarm>>

    fun start()
    fun stop()

    /** El código se valida en el servidor; la app nunca lo compara localmente (salvo el simulador de debug). */
    suspend fun acknowledge(alarmId: String, code: String): Result<Alarm>
}
