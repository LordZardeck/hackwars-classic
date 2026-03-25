package com.hackwars.rewrite.gamecore

import kotlin.math.min

internal data class FirewallCombatResolution(
    val targetDamage: Double,
    val attackBackDamage: Double,
)

internal class FirewallCombatResolver {
    fun resolve(
        sourcePort: PortState,
        targetPort: PortState,
        baseDamage: Double,
    ): FirewallCombatResolution {
        val profile = targetPort.installedFirewall?.combatProfile ?: FirewallCombatProfile()
        val modifier = profile.damageModifierFor(targetPort.type)
        val targetDamage = min(baseDamage * modifier, targetPort.health).coerceAtLeast(0.0)
        val attackBackDamage = if (targetDamage > 0.0) {
            min(profile.attackBackDamage, sourcePort.health).coerceAtLeast(0.0)
        } else {
            0.0
        }
        return FirewallCombatResolution(
            targetDamage = targetDamage,
            attackBackDamage = attackBackDamage,
        )
    }
}

internal val DefaultFirewallCombatResolver = FirewallCombatResolver()

private fun FirewallCombatProfile.damageModifierFor(portType: String): Double {
    return when (portType.lowercase()) {
        "bank", "banking" -> bankDamageModifier
        "ftp" -> ftpDamageModifier
        "http" -> httpDamageModifier
        "attack" -> attackDamageModifier
        "redirect" -> redirectDamageModifier
        else -> 1.0
    }
}
