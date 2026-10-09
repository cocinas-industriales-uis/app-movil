package co.edu.uis.cocinas.monitor

import co.edu.uis.cocinas.monitor.domain.AlarmCatalog
import co.edu.uis.cocinas.monitor.domain.ConnectionState
import co.edu.uis.cocinas.monitor.domain.KitchenReading
import co.edu.uis.cocinas.monitor.domain.Severity
import co.edu.uis.cocinas.monitor.util.Backoff
import co.edu.uis.cocinas.monitor.util.connectionStateFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun reading(priority: Int, received: Long = 0L) = KitchenReading(
    "cocina_01", 30.0, 400, 10, 1200, 29, false, 0.0, priority, 0, 0, 0, false, false, false, received,
)

class DomainTest {
    @Test fun severity_mapping_follows_firmware_priorities() {
        listOf(0, 1, 2, 3).forEach { assertEquals(Severity.EMERGENCY, Severity.fromPriority(it)) }
        listOf(4, 5).forEach { assertEquals(Severity.ELEVATED, Severity.fromPriority(it)) }
        listOf(6, 8).forEach { assertEquals(Severity.WARNING, Severity.fromPriority(it)) }
        listOf(7, 9).forEach { assertEquals(Severity.NORMAL, Severity.fromPriority(it)) }
    }

    @Test fun only_p0_to_p5_create_alarms() {
        (0..5).forEach { assertNotNull(AlarmCatalog.fromReading(reading(it), "id", 1L)) }
        (6..9).forEach { assertNull(AlarmCatalog.fromReading(reading(it), "id", 1L)) }
    }

    @Test fun stale_reading_detection() {
        val r = reading(9, received = 1_000L)
        assertFalse(r.isStale(1_000L + 14_000L))
        assertTrue(r.isStale(1_000L + 16_000L))
    }

    @Test fun backoff_doubles_up_to_cap_and_resets() {
        val b = Backoff()
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), List(7) { b.nextDelayMs() })
        b.reset()
        assertEquals(0, b.failures)
        assertEquals(1_000L, b.nextDelayMs())
    }

    @Test fun connection_state_by_consecutive_failures() {
        assertEquals(ConnectionState.CONNECTED, connectionStateFor(0))
        assertEquals(ConnectionState.RECONNECTING, connectionStateFor(2))
        assertEquals(ConnectionState.DISCONNECTED, connectionStateFor(3))
    }
}
