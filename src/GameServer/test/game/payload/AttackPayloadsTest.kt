package game.payload

import org.junit.Assert.assertEquals
import org.junit.Test

class AttackPayloadsTest {
    @Test
    fun attackContinuePayload_usesAttackContinueCommand() {
        assertEquals("attackcontinue", AttackContinuePayload.getCommand().wireName())
    }

    @Test
    fun firewallXpPayload_usesFirewallXpCommand() {
        assertEquals("firewallxp", CombatFirewallXpPayload(12.5f).getCommand().wireName())
    }
}
