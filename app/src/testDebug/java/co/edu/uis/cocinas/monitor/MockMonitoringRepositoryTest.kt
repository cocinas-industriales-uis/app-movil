package co.edu.uis.cocinas.monitor

import co.edu.uis.cocinas.monitor.data.mock.MockMonitoringRepository
import co.edu.uis.cocinas.monitor.domain.AlarmStatus
import co.edu.uis.cocinas.monitor.domain.InvalidCodeException
import co.edu.uis.cocinas.monitor.domain.Role
import co.edu.uis.cocinas.monitor.domain.Severity
import co.edu.uis.cocinas.monitor.domain.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MockMonitoringRepositoryTest {
    private val user = User("2", "Operador Demo", "operador", Role.OPERATOR)

    private fun MockMonitoringRepository.active() = alarms.value.filter { it.status == AlarmStatus.ACTIVE }

    @Test fun critical_scenario_creates_one_active_emergency_alarm() = runTest {
        val repo = MockMonitoringRepository(backgroundScope) { user }
        repo.simulate("cocina_02", 1)
        repo.simulate("cocina_02", 1)                       // misma condición: no duplica
        assertEquals(1, repo.active().size)
        assertEquals(Severity.EMERGENCY, repo.active().single().severity)
    }

    @Test fun alarm_stays_active_after_condition_clears_until_acknowledged() = runTest {
        val repo = MockMonitoringRepository(backgroundScope) { user }
        repo.simulate("cocina_02", 1)
        repo.simulate("cocina_02", 9)
        assertEquals(1, repo.active().size)
    }

    @Test fun wrong_code_is_rejected_and_alarm_continues() = runTest {
        val repo = MockMonitoringRepository(backgroundScope) { user }
        repo.simulate("cocina_02", 1)
        val r = repo.acknowledge(repo.active().single().id, "000000")
        assertTrue(r.exceptionOrNull() is InvalidCodeException)
        assertEquals(1, repo.active().size)
    }

    @Test fun right_code_acknowledges_and_records_who_and_when() = runTest {
        val repo = MockMonitoringRepository(backgroundScope) { user }
        repo.simulate("cocina_02", 1)
        val id = repo.active().single().id
        val a = repo.acknowledge(id, MockMonitoringRepository.MOCK_ACK_CODE).getOrThrow()
        assertEquals(AlarmStatus.ACKNOWLEDGED, a.status)
        assertEquals("Operador Demo", a.acknowledgedBy)
        assertTrue(a.acknowledgedAtMs != null)
        assertEquals(0, repo.active().size)
    }
}
