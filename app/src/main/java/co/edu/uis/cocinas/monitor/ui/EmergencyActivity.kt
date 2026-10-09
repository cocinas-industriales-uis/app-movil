package co.edu.uis.cocinas.monitor.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uis.cocinas.monitor.CocinasApp
import co.edu.uis.cocinas.monitor.domain.Alarm
import co.edu.uis.cocinas.monitor.domain.AlarmStatus
import co.edu.uis.cocinas.monitor.ui.screens.CallConfirmDialog
import co.edu.uis.cocinas.monitor.ui.screens.EmergencyScreen
import co.edu.uis.cocinas.monitor.ui.screens.PinDialog
import co.edu.uis.cocinas.monitor.ui.screens.dialFireDepartment
import co.edu.uis.cocinas.monitor.ui.theme.CocinasTheme

/** Pantalla completa de emergencia: se muestra sobre el bloqueo y se cierra sola cuando no quedan alarmas activas. */
class EmergencyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val container = (application as CocinasApp).container
        setContent {
            CocinasTheme {
                val ctx = LocalContext.current
                val vm: AckViewModel = viewModel(factory = VmFactory.factory)
                val ack by vm.state.collectAsStateWithLifecycle()
                val all by container.monitoring.alarms.collectAsStateWithLifecycle()
                val active = all.filter { it.status == AlarmStatus.ACTIVE }.sortedBy { it.priority }

                var target by remember { mutableStateOf<Alarm?>(null) }
                var confirmCall by remember { mutableStateOf(false) }

                LaunchedEffect(active.isEmpty()) { if (active.isEmpty()) finish() }
                LaunchedEffect(ack.success) {
                    if (ack.success) {
                        Toast.makeText(ctx, ack.message, Toast.LENGTH_LONG).show()
                        target = null
                        vm.consume()
                    }
                }

                if (active.isNotEmpty()) {
                    EmergencyScreen(active, onAcknowledge = { target = it; vm.consume() }, onCallFire = { confirmCall = true })
                }
                target?.let { a ->
                    PinDialog(
                        busy = ack.busy, message = ack.message,
                        onConfirm = { code -> vm.acknowledge(a.id, code) },
                        onDismiss = { target = null; vm.consume() },
                    )
                }
                if (confirmCall) {
                    CallConfirmDialog(
                        onConfirm = { confirmCall = false; dialFireDepartment(ctx) },
                        onDismiss = { confirmCall = false },
                    )
                }
            }
        }
    }
}
