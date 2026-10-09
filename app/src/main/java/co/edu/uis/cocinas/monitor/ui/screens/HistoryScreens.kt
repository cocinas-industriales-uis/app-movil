package co.edu.uis.cocinas.monitor.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.edu.uis.cocinas.monitor.domain.Alarm
import co.edu.uis.cocinas.monitor.domain.AlarmStatus
import co.edu.uis.cocinas.monitor.domain.kitchenName
import co.edu.uis.cocinas.monitor.ui.HistoryViewModel
import co.edu.uis.cocinas.monitor.ui.theme.StatusColors
import co.edu.uis.cocinas.monitor.ui.theme.onSeverityColor
import co.edu.uis.cocinas.monitor.ui.theme.severityColor
import co.edu.uis.cocinas.monitor.util.Fmt
import kotlinx.coroutines.delay

@Composable
private fun StatusPill(a: Alarm) {
    val active = a.status == AlarmStatus.ACTIVE
    Surface(color = if (active) severityColor(a.severity) else StatusColors.Stale, shape = MaterialTheme.shapes.small) {
        Text(
            if (active) "ACTIVA" else "Atendida",
            color = if (active) onSeverityColor(a.severity) else androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun HistoryScreen(vm: HistoryViewModel, onOpen: (String) -> Unit) {
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    if (alarms.isEmpty()) {
        Text("No hay alarmas registradas.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Spacer(Modifier.size(4.dp)) }
        items(alarms, key = { it.id }) { a ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(a.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${Fmt.date(a.startedAtMs)}  ${Fmt.time(a.startedAtMs)}", style = MaterialTheme.typography.labelLarge)
                        StatusPill(a)
                    }
                    Text(a.type, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${kitchenName(a.kitchenId)} · ${a.value}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    a.acknowledgedBy?.let {
                        Text("Atendida por $it a las ${Fmt.time(a.acknowledgedAtMs ?: a.startedAtMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Spacer(Modifier.size(16.dp)) }
    }
}

@Composable
fun AlarmDetailScreen(vm: HistoryViewModel, alarmId: String) {
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val a = alarms.firstOrNull { it.id == alarmId }
    if (a == null) {
        Text("Alarma no encontrada.", Modifier.padding(24.dp))
        return
    }
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(1_000); value = System.currentTimeMillis() } }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(a.type, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            StatusPill(a)
        }
        Spacer(Modifier.size(8.dp))
        TwoColumns(
            "Fecha" to Fmt.date(a.startedAtMs),
            "Hora" to Fmt.time(a.startedAtMs),
            "Cocina" to kitchenName(a.kitchenId),
            "Variable" to a.variable,
            "Valor detectado" to a.value,
            "Umbral" to (a.threshold ?: "—"),
            "Sensor" to a.sensor,
            "Prioridad" to "P${a.priority}",
            "Estado" to if (a.status == AlarmStatus.ACTIVE) "Activa" else "Atendida",
            "Tiempo activa" to Fmt.duration(a.activeDurationMs(now)),
            "Atendida por" to (a.acknowledgedBy ?: "—"),
            "Hora de reconocimiento" to (a.acknowledgedAtMs?.let { Fmt.time(it) } ?: "—"),
        )
    }
}
