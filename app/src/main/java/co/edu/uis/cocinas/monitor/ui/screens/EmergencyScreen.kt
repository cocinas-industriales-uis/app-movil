package co.edu.uis.cocinas.monitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uis.cocinas.monitor.domain.Alarm
import co.edu.uis.cocinas.monitor.domain.Severity
import co.edu.uis.cocinas.monitor.domain.kitchenName
import co.edu.uis.cocinas.monitor.ui.theme.severityColor
import co.edu.uis.cocinas.monitor.util.Fmt
import kotlinx.coroutines.delay

/** Jerarquía: EMERGENCIA → tipo de peligro → ubicación → valor detectado → acciones. */
@Composable
fun EmergencyScreen(alarms: List<Alarm>, onAcknowledge: (Alarm) -> Unit, onCallFire: () -> Unit) {
    val top = alarms.first()
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(1_000); value = System.currentTimeMillis() } }
    val isEmergency = top.severity == Severity.EMERGENCY

    Column(
        Modifier.fillMaxSize().background(severityColor(top.severity)).systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(if (isEmergency) "EMERGENCIA" else "RIESGO ELEVADO", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black)
            Text("ALERTA DE SEGURIDAD", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(28.dp))
            Text(top.type, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
            Spacer(Modifier.height(12.dp))
            Text(kitchenName(top.kitchenId), color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(top.value, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            top.threshold?.let { Text("Umbral: $it", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge) }
            Spacer(Modifier.height(12.dp))
            Text("Desde las ${Fmt.time(top.startedAtMs)} · activa ${Fmt.duration(top.activeDurationMs(now))}", color = Color.White.copy(alpha = 0.9f))
            if (alarms.size > 1) {
                Spacer(Modifier.height(8.dp))
                Text("+ ${alarms.size - 1} alarma(s) más activa(s)", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onAcknowledge(top) },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1B1F23)),
            ) { Text("RECONOCER / DESACTIVAR", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = onCallFire,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) { Text("🚒 LLAMAR A BOMBEROS", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            Text(
                "Esta app supervisa y notifica; las protecciones críticas actúan en el controlador.",
                color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
