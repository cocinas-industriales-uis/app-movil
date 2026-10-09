package co.edu.uis.cocinas.monitor.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import co.edu.uis.cocinas.monitor.CocinasApp
import co.edu.uis.cocinas.monitor.domain.Alarm
import co.edu.uis.cocinas.monitor.domain.AlarmStatus
import co.edu.uis.cocinas.monitor.domain.ConnectionState
import co.edu.uis.cocinas.monitor.domain.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano: mantiene viva la conexión de monitoreo con la app en segundo plano y es quien
 * dispara/detiene la alarma. Mientras exista una alarma EMERGENCIA activa (sin reconocer) suena y vibra.
 * LIMITACIÓN: si el sistema mata el proceso (ahorro de batería agresivo), la app no recibe nada hasta que se
 * reabra; para eso hace falta un push FCM de alta prioridad desde el backend (pendiente, ver README).
 */
class MonitoringService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: AlarmPlayer
    private val alerted = mutableSetOf<String>()
    private var started = false

    override fun onCreate() {
        super.onCreate()
        player = AlarmPlayer(this)
        Notifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as CocinasApp).container
        if (container.authRepository.user.value == null) { stopSelf(); return START_NOT_STICKY }

        val note = Notifications.monitor(this, "Supervisando cocinas…")
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, Notifications.ID_MONITOR, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.ID_MONITOR, note)
        }

        if (!started) {
            started = true
            container.monitoring.start()
            scope.launch { container.monitoring.alarms.collect { handleAlarms(it) } }
            scope.launch {
                container.monitoring.connection.collect { c ->
                    val text = when (c) {
                        ConnectionState.CONNECTED -> "Supervisando cocinas · conectado"
                        ConnectionState.RECONNECTING -> "Reconectando con el sistema de monitoreo…"
                        ConnectionState.DISCONNECTED -> "SIN CONEXIÓN con el sistema de monitoreo"
                    }
                    notify(Notifications.ID_MONITOR, Notifications.monitor(this@MonitoringService, text))
                }
            }
        }
        return START_STICKY
    }

    private fun handleAlarms(list: List<Alarm>) {
        val active = list.filter { it.status == AlarmStatus.ACTIVE }
        val critical = active.filter { it.severity == Severity.EMERGENCY }
        if (critical.isNotEmpty()) {
            player.start()
            val top = critical.minBy { it.priority }          // P0 es la más grave
            notify(Notifications.ID_CRITICAL, Notifications.critical(this, top, critical.size))
        } else {
            player.stop()
            NotificationManagerCompat.from(this).cancel(Notifications.ID_CRITICAL)
        }
        active.filter { it.severity == Severity.ELEVATED && alerted.add(it.id) }
            .forEach { notify(it.id.hashCode(), Notifications.alert(this, it)) }
    }

    @SuppressLint("MissingPermission")
    private fun notify(id: Int, n: android.app.Notification) {
        runCatching { NotificationManagerCompat.from(this).notify(id, n) }
            .onFailure { Log.w(TAG, "No se pudo notificar: ${it.javaClass.simpleName}") }
    }

    override fun onDestroy() {
        player.stop()
        (application as CocinasApp).container.monitoring.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "MonitoringService"
        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, MonitoringService::class.java))
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, MonitoringService::class.java)) }
    }
}
