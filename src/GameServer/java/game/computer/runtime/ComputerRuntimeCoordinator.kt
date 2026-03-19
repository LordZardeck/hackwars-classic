package game.computer.runtime

class ComputerRuntimeCoordinator(
    private val saveLogoutTickService: SaveLogoutTickService = SaveLogoutTickService(),
    private val activityPingTickService: ActivityPingTickService = ActivityPingTickService(),
    private val incomeTickService: IncomeTickService = IncomeTickService(),
    private val combatCpuOverheatTickService: CombatCpuOverheatTickService = CombatCpuOverheatTickService(),
    private val abuseProtectionTickService: AbuseProtectionTickService = AbuseProtectionTickService()
) {
    fun tick(state: RuntimeTickState): List<RuntimeTickEvent> {
        val events = mutableListOf<RuntimeTickEvent>()
        events += saveLogoutTickService.tick(state)
        events += activityPingTickService.tick(state)
        events += incomeTickService.tick(state)
        events += combatCpuOverheatTickService.tick(state)
        events += abuseProtectionTickService.tick(state)
        return events
    }
}
