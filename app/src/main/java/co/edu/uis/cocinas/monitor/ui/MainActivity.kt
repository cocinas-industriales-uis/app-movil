package co.edu.uis.cocinas.monitor.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import co.edu.uis.cocinas.monitor.AppContainer
import co.edu.uis.cocinas.monitor.CocinasApp
import co.edu.uis.cocinas.monitor.domain.AlarmStatus
import co.edu.uis.cocinas.monitor.domain.Severity
import co.edu.uis.cocinas.monitor.domain.User
import co.edu.uis.cocinas.monitor.service.MonitoringService
import co.edu.uis.cocinas.monitor.ui.screens.AlarmDetailScreen
import co.edu.uis.cocinas.monitor.ui.screens.DashboardScreen
import co.edu.uis.cocinas.monitor.ui.screens.HistoryScreen
import co.edu.uis.cocinas.monitor.ui.screens.LoginScreen
import co.edu.uis.cocinas.monitor.ui.screens.ProfileScreen
import co.edu.uis.cocinas.monitor.ui.theme.CocinasTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as CocinasApp).container

        // Con la app abierta, una emergencia nueva abre la pantalla completa de inmediato.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.monitoring.alarms
                    .map { l -> l.any { it.status == AlarmStatus.ACTIVE && it.severity == Severity.EMERGENCY } }
                    .distinctUntilChanged()
                    .collect { critical ->
                        if (critical && container.authRepository.user.value != null) {
                            startActivity(Intent(this@MainActivity, EmergencyActivity::class.java))
                        }
                    }
            }
        }

        setContent { CocinasTheme { MainApp(container) } }
    }
}

@Composable
private fun MainApp(container: AppContainer) {
    val ctx = LocalContext.current
    val authVm: AuthViewModel = viewModel(factory = VmFactory.factory)
    val user by authVm.user.collectAsStateWithLifecycle()
    val login by authVm.state.collectAsStateWithLifecycle()

    // La supervisión (y su servicio en primer plano) solo existe con sesión iniciada.
    LaunchedEffect(user?.id) {
        if (user != null) MonitoringService.start(ctx) else MonitoringService.stop(ctx)
    }

    val u = user
    if (u == null) LoginScreen(login, authVm::login) else Shell(u, authVm::logout, container)
}

private enum class Tab(val route: String, val title: String) {
    Dashboard("dashboard", "Panel"), History("history", "Historial"), Profile("profile", "Perfil")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Shell(user: User, onLogout: () -> Unit, container: AppContainer) {
    val ctx = LocalContext.current
    val nav = rememberNavController()
    val backEntry by nav.currentBackStackEntryAsState()
    val dest = backEntry?.destination
    val dashVm: DashboardViewModel = viewModel(factory = VmFactory.factory)
    val histVm: HistoryViewModel = viewModel(factory = VmFactory.factory)

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    when {
                        dest?.route == Tab.History.route -> "Historial de alarmas"
                        dest?.route == Tab.Profile.route -> "Perfil"
                        dest?.route?.startsWith("alarm/") == true -> "Detalle de alarma"
                        else -> "Cocinas Monitor"
                    }
                )
            })
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = dest?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                when (tab) {
                                    Tab.Dashboard -> Icons.Filled.Home
                                    Tab.History -> Icons.AutoMirrored.Filled.List
                                    Tab.Profile -> Icons.Filled.Person
                                }, contentDescription = tab.title
                            )
                        },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            NavHost(nav, startDestination = Tab.Dashboard.route) {
                composable(Tab.Dashboard.route) {
                    DashboardScreen(dashVm, container.mock) { ctx.startActivity(Intent(ctx, EmergencyActivity::class.java)) }
                }
                composable(Tab.History.route) { HistoryScreen(histVm) { id -> nav.navigate("alarm/$id") } }
                composable("alarm/{id}") { e -> AlarmDetailScreen(histVm, e.arguments?.getString("id").orEmpty()) }
                composable(Tab.Profile.route) { ProfileScreen(user, onLogout) }
            }
        }
    }
}
