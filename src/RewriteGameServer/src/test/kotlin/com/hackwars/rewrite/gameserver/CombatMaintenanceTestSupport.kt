package com.hackwars.rewrite.gameserver

import com.hackwars.rewrite.gamecore.CombatMaintenanceProgramRegistry
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.ProgramHandle

internal object DisabledCombatMaintenanceProgramRegistry : CombatMaintenanceProgramRegistry {
    override suspend fun register(
        stateId: GameStateId,
        programId: String,
        handle: ProgramHandle,
    ) = Unit

    override suspend fun programIdFor(stateId: GameStateId): String? = null

    override suspend fun hasProgram(stateId: GameStateId): Boolean = true

    override suspend fun cancel(stateId: GameStateId, reason: String): Boolean = false

    override suspend fun unregister(programId: String) = Unit
}
