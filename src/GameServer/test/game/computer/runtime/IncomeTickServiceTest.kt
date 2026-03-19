package game.computer.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(412.5f, state.bankMoney, 0.0001f)
        assertEquals(3, events.filterIsInstance<RuntimeTickEvent.LogEntry>().size)
        assertEquals(1, events.filterIsInstance<RuntimeTickEvent.DailyPayIssued>().size)
        assertEquals(1, events.filterIsInstance<RuntimeTickEvent.HttpXpIssued>().size)
        assertEquals(1, events.filterIsInstance<RuntimeTickEvent.PettyCashAdded>().size)
        assertEquals(1, events.filterIsInstance<RuntimeTickEvent.BankMoneyAdded>().size)
    }

    @Test
    fun tick_clearsInactiveFlagAndWritesInactiveLog() {
        val state = baseRuntimeState(now = 20_000L)
        state.inactive = true
        state.lastPaid = 10_000L
        state.payPeriodMs = 5_000L

        val events = service.tick(state)

        assertFalse(state.inactive)
        assertEquals(20_000L, state.lastPaid)
        assertEquals(1, events.filterIsInstance<RuntimeTickEvent.LogEntry>().size)
    }
}
