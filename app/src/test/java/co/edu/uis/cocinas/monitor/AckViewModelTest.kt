package co.edu.uis.cocinas.monitor

import co.edu.uis.cocinas.monitor.domain.Alarm
import co.edu.uis.cocinas.monitor.domain.AlarmCatalog
import co.edu.uis.cocinas.monitor.domain.AlarmStatus
import co.edu.uis.cocinas.monitor.domain.ConnectionState
import co.edu.uis.cocinas.monitor.domain.InvalidCodeException
import co.edu.uis.cocinas.monitor.domain.KitchenReading
import co.edu.uis.cocinas.monitor.domain.MonitoringRepository
import co.edu.uis.cocinas.monitor.ui.AckViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AckViewModelTest {
    private class FakeRepo : MonitoringRepository {
        override val connection: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.CONNECTED)
        override val readings: StateFlow<Map<String, KitchenReading>> = MutableStateFlow(emptyMap())
        override val alarms: StateFlow<List<Alarm>> = MutableStateFlow(emptyList())
        var calls = 0
        override fun start() {}
        override fun stop() {}
        override suspend fun acknowledge(alarmId: String, code: String): Result<Alarm> {
            calls++
            if (code != "111111") return Result.failure(InvalidCodeException())
            val i = AlarmCatalog.info(1)
            return Result.success(Alarm(alarmId, "cocina_01", i.type, i.variable, i.sensor, "x", null, 1, 0, AlarmStatus.ACKNOWLEDGED, "u", 1))
        }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun wrong_code_keeps_alarm_active_message() {
        val vm = AckViewModel(FakeRepo())
        vm.acknowledge("a1", "000000", nowMs = 0)
        assertEquals("Código incorrecto. La alarma continúa activa.", vm.state.value.message)
        assertFalse(vm.state.value.success)
    }

    @Test fun right_code_acknowledges() {
        val vm = AckViewModel(FakeRepo())
        vm.acknowledge("a1", "111111", nowMs = 0)
        assertEquals("Alarma reconocida correctamente.", vm.state.value.message)
        assertTrue(vm.state.value.success)
    }

    @Test fun five_wrong_codes_lock_attempts_for_30s() {
        val repo = FakeRepo()
        val vm = AckViewModel(repo)
        repeat(AckViewModel.MAX_FAILURES) { vm.acknowledge("a1", "000000", nowMs = 0) }
        val before = repo.calls
        vm.acknowledge("a1", "111111", nowMs = 1_000)            // aunque sea correcto, está bloqueado
        assertEquals(before, repo.calls)
        assertFalse(vm.state.value.success)
        vm.acknowledge("a1", "111111", nowMs = AckViewModel.LOCK_MS + 1)   // pasado el bloqueo
        assertTrue(vm.state.value.success)
    }
}
