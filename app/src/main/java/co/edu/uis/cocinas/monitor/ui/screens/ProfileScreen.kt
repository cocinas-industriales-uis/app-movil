package co.edu.uis.cocinas.monitor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.edu.uis.cocinas.monitor.BuildConfig
import co.edu.uis.cocinas.monitor.domain.User

@Composable
fun ProfileScreen(user: User, onLogout: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(user.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(12.dp))
        TwoColumns(
            "Usuario" to user.username,
            "Rol" to user.role.label,
            "Estado de sesión" to "Activa",
            "Versión de la app" to BuildConfig.VERSION_NAME,
            "Número de bomberos configurado" to BuildConfig.EMERGENCY_FIRE_DEPARTMENT_NUMBER,
            "Modo" to if (BuildConfig.USE_MOCK) "Simulado (debug)" else "Servidor real",
        )
        Spacer(Modifier.size(24.dp))
        Text(
            "Esta app es una interfaz de supervisión y notificación. Las protecciones críticas actúan en el controlador del sistema.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(16.dp))
        Button(
            onClick = onLogout, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) { Text("CERRAR SESIÓN") }
    }
}
