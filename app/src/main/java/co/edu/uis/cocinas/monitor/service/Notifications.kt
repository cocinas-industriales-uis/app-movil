package co.edu.uis.cocinas.monitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import co.edu.uis.cocinas.monitor.domain.Alarm
import co.edu.uis.cocinas.monitor.domain.kitchenName
import co.edu.uis.cocinas.monitor.ui.EmergencyActivity
import co.edu.uis.cocinas.monitor.ui.MainActivity

object Notifications {
    const val CH_MONITOR = "monitor"
    const val CH_CRITICAL = "alarm_critical"
    const val CH_ALERTS = "alerts"
    const val ID_MONITOR = 1
    const val ID_CRITICAL = 2

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_MONITOR, "Supervisión activa", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Indica que la app está supervisando las cocinas" }
        )
        // El sonido y la vibración de la emergencia los hace AlarmPlayer (en bucle, canal USAGE_ALARM),
        // por eso este canal no define sonido propio: así no suena doble.
        nm.createNotificationChannel(
            NotificationChannel(CH_CRITICAL, "Emergencias críticas", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alarmas de emergencia (gas, incendio)"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Alertas de riesgo", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Prealertas y riesgo elevado" }
        )
    }

    private fun pending(ctx: Context, target: Class<*>, req: Int): PendingIntent {
        val i = Intent(ctx, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun monitor(ctx: Context, text: String): Notification =
        NotificationCompat.Builder(ctx, CH_MONITOR)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Cocinas Monitor")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pending(ctx, MainActivity::class.java, 10))
            .build()

    fun critical(ctx: Context, top: Alarm, total: Int): Notification {
        val pi = pending(ctx, EmergencyActivity::class.java, 20)
        val extra = if (total > 1) " (+${total - 1} más)" else ""
        return NotificationCompat.Builder(ctx, CH_CRITICAL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("EMERGENCIA · ${kitchenName(top.kitchenId)}$extra")
            .setContentText("${top.type}: ${top.value}")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
    }

    fun alert(ctx: Context, a: Alarm): Notification =
        NotificationCompat.Builder(ctx, CH_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("${a.type} · ${kitchenName(a.kitchenId)}")
            .setContentText(a.value)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pending(ctx, EmergencyActivity::class.java, 30))
            .build()
}
