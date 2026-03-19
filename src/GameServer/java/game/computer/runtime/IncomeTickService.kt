package game.computer.runtime

class IncomeTickService {
    fun tick(state: RuntimeTickState): List<RuntimeTickEvent> {
        val events = mutableListOf<RuntimeTickEvent>()

        if (state.lastPaid <= 100L) {
            state.lastPaid = state.now
        }

        if (state.now - state.lastPaid > state.payPeriodMs && state.loaded && !state.inactive) {
            state.myVotes = minOf(4, state.myVotes + 1)

            if (!state.httpActive) {
                events += RuntimeTickEvent.LogEntry(
                    "Did not receive income from website because HTTP is not installed.",
                    state.ip,
                    state.lastPaid + state.payPeriodMs
                )
                state.lastPaid = state.now
                return events
            }

            val mod = if (state.npc) 0f else (state.httpLevel - 1.0f) * 50.0f
            val totalPay = state.dailyPaySize + mod
            val amount = totalPay * 0.75f * state.dailyPayReduction
            val extra = totalPay * 0.75f - amount
            if (extra > 0f) {
                state.pettyCash += extra
                events += RuntimeTickEvent.PettyCashAdded(extra)
                events += RuntimeTickEvent.LogEntry(
                    "Transferred ${java.text.NumberFormat.getCurrencyInstance().format(extra)} of daily pay from ${state.ip}.",
                    state.ip,
                    state.lastPaid + state.payPeriodMs
                )
            }

            events += RuntimeTickEvent.DailyPayIssued(amount, state.adRevenueTarget)
            events += RuntimeTickEvent.HttpXpIssued(state.httpLevel * 10.0f, state.adRevenueTarget)
            events += RuntimeTickEvent.ApplicationDataDispatchRequested(
                RuntimeApplicationDataDispatch(
                    function = "logmessage",
                    parameters = arrayOf<Any>(
                        "Transferred ${java.text.NumberFormat.getCurrencyInstance().format(amount)} of daily pay from ${state.ip}.",
                        state.ip,
                        state.lastPaid + state.payPeriodMs
                    ),
                    sourceIp = state.ip,
                ),
                state.adRevenueTarget
            )

            val guaranteedBankAmount = totalPay * 0.25f
            events += RuntimeTickEvent.LogEntry(
                "Received $$guaranteedBankAmount in guaranteed income to bank.",
                state.ip,
                state.lastPaid + state.payPeriodMs
            )
            events += RuntimeTickEvent.BankMoneyAdded(guaranteedBankAmount)
            state.bankMoney += guaranteedBankAmount
            state.lastPaid += state.payPeriodMs
            return events
        }

        if (state.inactive) {
            events += RuntimeTickEvent.LogEntry(
                "Did not receive income because you were inactive.",
                state.ip,
                state.now
            )
            state.lastPaid = state.now
            state.inactive = false
            return events
        }

        return events
    }
}
