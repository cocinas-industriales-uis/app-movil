package co.edu.uis.cocinas.monitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import co.edu.uis.cocinas.monitor.domain.Severity

/** Los colores saturados se reservan SOLO para estados de seguridad. El resto de la UI es neutra. */
object StatusColors {
    val Normal = Color(0xFF2E7D32)
    val Warning = Color(0xFFF9A825)
    val Elevated = Color(0xFFEF6C00)
    val Emergency = Color(0xFFC62828)
    val Stale = Color(0xFF757575)
}

fun severityColor(s: Severity?): Color = when (s) {
    Severity.NORMAL -> StatusColors.Normal
    Severity.WARNING -> StatusColors.Warning
    Severity.ELEVATED -> StatusColors.Elevated
    Severity.EMERGENCY -> StatusColors.Emergency
    null -> StatusColors.Stale
}

/** Texto legible sobre el color de severidad (el amarillo necesita texto oscuro). */
fun onSeverityColor(s: Severity?): Color = if (s == Severity.WARNING) Color(0xFF1B1F23) else Color.White

fun severityLabel(s: Severity?): String = when (s) {
    Severity.NORMAL -> "SISTEMA NORMAL"
    Severity.WARNING -> "ADVERTENCIA"
    Severity.ELEVATED -> "RIESGO ELEVADO"
    Severity.EMERGENCY -> "EMERGENCIA"
    null -> "SIN DATOS"
}

private val LightScheme = lightColorScheme(
    primary = Color(0xFF37474F), onPrimary = Color.White,
    background = Color(0xFFF4F5F6), onBackground = Color(0xFF1B1F23),
    surface = Color.White, onSurface = Color(0xFF1B1F23),
    surfaceVariant = Color(0xFFE6E8EA), onSurfaceVariant = Color(0xFF464B50),
    outline = Color(0xFF9AA0A6), error = StatusColors.Emergency,
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB0BEC5), onPrimary = Color(0xFF10151A),
    background = Color(0xFF121416), onBackground = Color(0xFFE6E8EA),
    surface = Color(0xFF1C1F22), onSurface = Color(0xFFE6E8EA),
    surfaceVariant = Color(0xFF2A2E32), onSurfaceVariant = Color(0xFFBFC4C9),
    outline = Color(0xFF6B7178), error = Color(0xFFEF5350),
)

@Composable
fun CocinasTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
