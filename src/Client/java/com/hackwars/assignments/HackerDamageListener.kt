package com.hackwars.assignments

import assignments.DamageAssignment
import com.hackwars.net.DamageEvent
import com.hackwars.net.decodeDamageEvent
import gui.Hacker

class HackerDamageListener : DamageAssignmentListener {
    val lock = Any()
    var receiver: Hacker? = null
        set(value) {
            synchronized(lock) {
                field = value
            }
        }

    override fun onDamageAssignment(event: AssignmentEvent<DamageAssignment>) {
        val assignment = event.assignment

        synchronized(lock) {
            receiver?.let { receiver ->
                if(receiver.loading) return

                receiver.setAttackXP(assignment.attackXP)
                receiver.setMerchantingXP(assignment.merchantXP)
                receiver.setWatchXP(assignment.watchXP)
                receiver.setScanXP(assignment.scanningXP)
                receiver.setFirewallXP(assignment.fireWallXP)
                receiver.setHTTPXP(assignment.httpxp)
                receiver.setRedirectXP(assignment.redirectXP)
                receiver.setRepairXP(assignment.repairXP)
                receiver.statsPanel.cpuLoadIcon.load = assignment.cpuCost.toInt().toFloat()
                receiver.setHealth(assignment.healthUpdates)

                assignment.damage.orEmpty()
                    .mapNotNull(::decodeDamageEvent)
                    .forEach { event ->
                        when (event) {
                            is DamageEvent.Attack -> receiver.showAttackMessage(
                                event.amount,
                                event.windowHandle,
                                event.firewall,
                                event.mining
                            )

                            is DamageEvent.Zombie -> receiver.showZombieMessage(
                                event.amount,
                                event.windowHandle,
                                event.ip
                            )

                            is DamageEvent.Attacking -> receiver.setAttacking(
                                event.windowHandle,
                                event.attacking
                            )
                        }
                    }
            }
        }
    }
}