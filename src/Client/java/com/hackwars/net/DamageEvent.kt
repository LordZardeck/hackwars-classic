package com.hackwars.net

sealed interface DamageEvent {
    val windowHandle: Int

    data class Attack(
        override val windowHandle: Int,
        val amount: Float,
        val firewall: Boolean,
        val mining: Boolean
    ) : DamageEvent

    data class Zombie(
        override val windowHandle: Int,
        val amount: Float,
        val ip: String?,
    ) : DamageEvent

    data class Attacking(
        override val windowHandle: Int,
        val attacking: Boolean
    ) : DamageEvent
}

fun decodeDamageEvent(raw: Any?): DamageEvent? {
    val d = raw as? Array<*> ?: return null
    val handle = d.getOrNull(0) as? Int ?: return null
    return when (val attacking = d.getOrNull(1)) {
        is Boolean -> DamageEvent.Attacking(handle, attacking)
        is Float -> {
            if (d.size > 4) {
                DamageEvent.Zombie(
                    handle,
                    attacking,
                    d.getOrNull(2) as? String,
                )
            } else {
                DamageEvent.Attack(
                    handle,
                    attacking,
                    d.getOrNull(2) as? Boolean ?: return null,
                    d.getOrNull(3) as? Boolean ?: return null
                )
            }
        }

        else -> null
    }
}