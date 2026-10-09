package co.edu.uis.cocinas.monitor.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import co.edu.uis.cocinas.monitor.BuildConfig
import co.edu.uis.cocinas.monitor.domain.ConnectionState
import co.edu.uis.cocinas.monitor.domain.Severity
import co.edu.uis.cocinas.monitor.ui.theme.StatusColors
import co.edu.uis.cocinas.monitor.ui.theme.onSeverityColor
import co.edu.uis.cocinas.monitor.ui.theme.severityColor
import co.edu.uis.cocinas.monitor.ui.theme.severityLabel
import co.edu.uis.cocinas.monitor.util.Fmt

/** Abre el marcador con el número configurado. NUNCA llama automáticamente: el usuario pulsa "llamar". */
fun dialFireDepartment(ctx: Context) {
    ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${BuildConfig.EMERGENCY_FIRE_DEPARTMENT_NUMBER}")))
}

@Composable
fun StatusDot(color: androidx.compose.ui.graphics.Color, size: Int = 12) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun ConnectionChip(state: ConnectionState, lastUpdateAgoMs: Long?) {
    val (color, text) = when (state) {
        ConnectionState.CONNECTED -> StatusColors.Normal to "Conectado"
        ConnectionState.RECONNECTING -> StatusColors.Warning to "Reconectando…"
        ConnectionState.DISCONNECTED -> StatusColors.Emergency to "Sin conexión"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(
                lastUpdateAgoMs?.let { "Última actualización: ${Fmt.ago(it)}" } ?: "Sin datos recibidos",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SeverityBanner(severity: Severity?, activeAlarms: Int, onAttend: () -> Unit) {
    Surface(color = severityColor(severity), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("ESTADO DEL SISTEMA", style = MaterialTheme.typography.labelMedium, color = onSeverityColor(severity).copy(alpha = 0.85f))
            Text(
                severityLabel(severity),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = onSeverityColor(severity),
            )
            if (activeAlarms > 0) {
                Spacer(Modifier.size(8.dp))
                Text(
                    "$activeAlarms alarma(s) activa(s) sin reconocer",
                    color = onSeverityColor(severity),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = onAttend,
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.White, contentColor = androidx.compose.ui.graphics.Color(0xFF1B1F23)),
                ) { Text("ATENDER ALARMAS") }
            }
        }
    }
}

/** Diálogo de código de seguridad. El código se envía al backend para validarse; aquí nunca se compara. */
@Composable
fun PinDialog(busy: Boolean, message: String?, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("AUTORIZACIÓN REQUERIDA") },
        text = {
            Column {
                Text("Ingrese el código de seguridad para desactivar la alarma.")
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.filter { it.isDigit() }.take(8) },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    label = { Text("Código") },
                )
                if (message != null) {
                    Spacer(Modifier.size(8.dp))
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(code) }, enabled = code.length >= 4 && !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("CONFIRMAR")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("CANCELAR") } },
    )
}

@Composable
fun CallConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Llamar a bomberos?") },
        text = { Text("Se abrirá el marcador con el número ${BuildConfig.EMERGENCY_FIRE_DEPARTMENT_NUMBER}. Deberás pulsar \"llamar\" para iniciar la llamada.") },
        confirmButton = { Button(onClick = onConfirm) { Text("ABRIR MARCADOR") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } },
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun TwoColumns(vararg items: Pair<String, String>) {
    items.toList().chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { (l, v) -> LabeledValue(l, v, Modifier.weight(1f)) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
