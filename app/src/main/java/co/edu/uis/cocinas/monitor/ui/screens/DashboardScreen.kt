package co.edu.uis.cocinas.monitor.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.edu.uis.cocinas.monitor.data.mock.MockMonitoringRepository
import co.edu.uis.cocinas.monitor.domain.ConnectionState
import co.edu.uis.cocinas.monitor.domain.Severity
import co.edu.uis.cocinas.monitor.domain.kitchenName
import co.edu.uis.cocinas.monitor.ui.DashboardViewModel
import co.edu.uis.cocinas.monitor.ui.KitchenUi
import co.edu.uis.cocinas.monitor.ui.theme.StatusColors
import co.edu.uis.cocinas.monitor.ui.theme.onSeverityColor
import co.edu.uis.cocinas.monitor.ui.theme.severityColor
import co.edu.uis.cocinas.monitor.ui.theme.severityLabel
import co.edu.uis.cocinas.monitor.util.Fmt
import java.util.Locale

private fun missingNotifications(ctx: Context): Boolean =
    Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

private fun missingFullScreen(ctx: Context): Boolean =
    Build.VERSION.SDK_INT >= 34 && !ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

@Composable
fun DashboardScreen(vm: DashboardViewModel, mock: MockMonitoringRepository?, onAttendAlarms: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    var noNotif by remember { mutableStateOf(missingNotifications(ctx)) }
    var noFsi by remember { mutableStateOf(missingFullScreen(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { noNotif = missingNotifications(ctx); noFsi = missingFullScreen(ctx) }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { noNotif = !it }

    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.size(4.dp)) }

        if (noNotif || noFsi) item {
            Card(colors = CardDefaults.cardColors(containerColor = StatusColors.Warning.copy(alpha = 0.18f))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Faltan permisos para que la alarma funcione", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Sin ellos, una emergencia con el teléfono bloqueado o la app en segundo plano podría no avisar.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.size(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (noNotif) OutlinedButton(onClick = { askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Notificaciones") }
                        if (noFsi && Build.VERSION.SDK_INT >= 34) OutlinedButton(onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")))
                        }) { Text("Pantalla completa") }
                    }
                }
            }
        }

        if (s.connection != ConnectionState.CONNECTED) item {
            Surface(color = if (s.connection == ConnectionState.DISCONNECTED) StatusColors.Emergency else StatusColors.Warning, shape = MaterialTheme.shapes.medium) {
                Text(
                    if (s.connection == ConnectionState.DISCONNECTED) "⚠️ Se perdió la comunicación con el sistema de monitoreo. Los datos mostrados pueden estar desactualizados."
                    else "Reconectando con el sistema de monitoreo… Los datos mostrados pueden estar desactualizados.",
                    color = onSeverityColor(if (s.connection == ConnectionState.DISCONNECTED) Severity.EMERGENCY else Severity.WARNING),
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item { SeverityBanner(s.overall, s.activeAlarms, onAttendAlarms) }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    ConnectionChip(s.connection, s.lastUpdateAgoMs)
                    LabeledValue("Cocinas reportando", "${s.reporting} / ${s.kitchens.size}")
                    LabeledValue("Alarmas activas", s.activeAlarms.toString())
                }
            }
        }

        item { SectionTitle("COCINAS") }
        if (s.kitchens.isEmpty()) item { Text("Esperando datos del sistema…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(s.kitchens, key = { it.reading.kitchenId }) { KitchenCard(it) }

        if (mock != null) item { SimulationPanel(mock) }
        item { Spacer(Modifier.size(16.dp)) }
    }
}

@Composable
private fun KitchenCard(k: KitchenUi) {
    val r = k.reading
    val sev = if (k.stale) null else r.severity
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(kitchenName(r.kitchenId), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(color = severityColor(sev), shape = MaterialTheme.shapes.small) {
                    Text(
                        if (k.stale) "SIN DATOS" else severityLabel(r.severity),
                        color = onSeverityColor(sev), style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            if (k.stale) {
                Spacer(Modifier.size(6.dp))
                Text("DATOS DESACTUALIZADOS · último dato ${Fmt.ago(k.ageMs)}", color = StatusColors.Emergency, style = MaterialTheme.typography.labelLarge)
            } else if (r.confirming) {
                Spacer(Modifier.size(6.dp))
                Text("Verificando condición detectada…", color = StatusColors.Elevated, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.size(8.dp))
            val fmt = { v: Double -> String.format(Locale.US, "%.1f", v) }
            TwoColumns(
                "Temperatura" to "${fmt(r.temperature)} °C",
                "Gas" to "${r.gasPct} % (raw ${r.gasRaw})",
                "Presión" to "${r.pressurePct} % (raw ${r.pressureRaw})",
                "Llama" to if (r.flame) "DETECTADA" else "No",
                "ΔT" to "${fmt(r.deltaT)} °C/s",
                "Prioridad" to "P${r.priority}",
                "Ventiladores" to "V1 ${r.fan1Pct}% · V2 ${r.fan2Pct}% · V3 ${r.fan3Pct}%",
                "Válvula de gas" to if (r.valveClosed) "CERRADA" else "Abierta",
                "Aspersores" to if (r.sprinklerOn) "ACTIVOS" else "Apagados",
            )
        }
    }
}

/** Solo en debug/simulado: dispara escenarios para probar alarma, sonido, vibración y pérdida de conexión. */
@Composable
private fun SimulationPanel(mock: MockMonitoringRepository) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PANEL DE SIMULACIÓN (solo debug)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("Código de reconocimiento simulado: ${MockMonitoringRepository.MOCK_ACK_CODE}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { mock.simulate("cocina_02", 4) }) { Text("Prealerta gas") }
                OutlinedButton(onClick = { mock.simulate("cocina_02", 1) }) { Text("Gas crítico") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { mock.simulate("cocina_03", 3) }) { Text("Incendio mod.") }
                OutlinedButton(onClick = { mock.simulate("cocina_03", 0) }) { Text("Gas + llama") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { MockMonitoringRepository.KITCHENS.forEach { mock.simulate(it, 9) } }) { Text("Normalizar") }
                OutlinedButton(onClick = { mock.setLinkUp(false) }) { Text("Cortar enlace") }
                OutlinedButton(onClick = { mock.setLinkUp(true) }) { Text("Restaurar") }
            }
        }
    }
}
