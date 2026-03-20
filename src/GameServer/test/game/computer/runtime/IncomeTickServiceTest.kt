package game.computer.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeTickServiceTest {
    private val service = IncomeTickService()

    @Test
    fun tick_issuesDailyPayAndBankIncomeWhenHTTPIsActive() {
        val state = baseRuntimeState(now = 20_000L)
        state.lastPaid = 9_000L
        state.payPeriodMs = 10_000L
        state.dailyPaySize = 1_000f
        state.dailyPayReduction = 0.5f
        state.httpActive = true
        state.httpLevel = 3f
        state.myVotes = 3

        val events = service.tick(state)

        assertEquals(4, state.myVotes)
        assertEquals(412.5f, state.pettyCash, 0.0001f)
        assertEquals(275f, state.bankMoney, 0.0001f)
        assertEquals(19_000L, state.lastPaid)
        assertEquals(2, events.filterIsInstance<RuntimeTickEvent.LogEntry>().size)
        assertEquals(1, events.filterIsInstance<RuntimeTickEvent.ApplicationDataDispatchRequested>().size)
        assertEquals(1, events.filterIsInstance<RuntimeTickEvent.DailyPayIssued>().size)
        assertEquals(1, events.filterIsInstance<RuntimeTickEvent.HttpXpIssued>().size)
        assertEquals(1, events.filterIsInstance<RuntimeTickEvent.PettyCashAdded>().size)
        assertEquals(1, events.filterIsInstance<RuntimeTickEvent.BankMoneyAdded>().size)
        assertTrue(events.contains(RuntimeTickEvent.DailyPayIssued(412.5f, "7.7.7.7")))
        assertTrue(events.contains(RuntimeTickEvent.BankMoneyAdded(275f)))
        val revenueLog = events.filterIsInstance<RuntimeTickEvent.ApplicationDataDispatchRequested>().single()
        assertEquals("7.7.7.7", revenueLog.targetIp)
        assertEquals("logmessage", revenueLog.applicationData.command.wireName())
        assertEquals("5.5.5.5", revenueLog.applicationData.sourceIp)
        val payload = revenueLog.applicationData.payload as RuntimeLogMessagePayload
        assertTrue(payload.message.contains("daily pay"))
        assertEquals("5.5.5.5", payload.ip)
        assertEquals(19_000L, payload.timestamp)
    }

    @Test
    fun tick_httpMissing_logsAndResetsLastPaidWithoutIssuingIncome() {
        val state = baseRuntimeState(now = 20_000L)
        state.lastPaid = 9_000L
        state.payPeriodMs = 10_000L
        state.httpActive = false
        state.myVotes = 4

        val events = service.tick(state)

        assertEquals(4, state.myVotes)
        assertEquals(20_000L, state.lastPaid)
        assertEquals(1, events.filterIsInstance<RuntimeTickEvent.LogEntry>().size)
        assertTrue(events.none { it is RuntimeTickEvent.DailyPayIssued })
        assertTrue(events.none { it is RuntimeTickEvent.BankMoneyAdded })
        assertTrue(events.none { it is RuntimeTickEvent.ApplicationDataDispatchRequested })
    }

    @Test
    fun tick_inactiveLogsAndResetsEvenBeforePayWindowExpires() {
        val state = baseRuntimeState(now = 6_000L)
        state.inactive = true
        state.lastPaid = 2_000L
        state.payPeriodMs = 10_000L

        val events = service.tick(state)

        assertFalse(state.inactive)
        assertEquals(6_000L, state.lastPaid)
        assertEquals(1, events.filterIsInstance<RuntimeTickEvent.LogEntry>().size)
    }

    @Test
    fun tick_notLoadedDoesNotIssueIncomeOrResetLastPaid() {
        val state = baseRuntimeState(now = 20_000L)
        state.loaded = false
        state.lastPaid = 9_000L
        state.payPeriodMs = 10_000L

        val events = service.tick(state)

        assertTrue(events.isEmpty())
        assertEquals(9_000L, state.lastPaid)
        assertEquals(0f, state.bankMoney, 0.0001f)
        assertEquals(0f, state.pettyCash, 0.0001f)
    }

    @Test
    fun tick_initialLastPaidIsSeededWithoutIssuingIncome() {
        val state = baseRuntimeState(now = 20_000L)
        state.lastPaid = 0L
        state.payPeriodMs = 10_000L

        val events = service.tick(state)

        assertTrue(events.isEmpty())
        assertEquals(20_000L, state.lastPaid)
        assertEquals(0f, state.bankMoney, 0.0001f)
        assertEquals(0f, state.pettyCash, 0.0001f)
    }
}
