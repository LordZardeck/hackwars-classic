package com.hackwars.rewrite.gamecore

internal data class AttackActorContext(
    val hostStateId: GameStateId,
    val actorStateId: GameStateId,
    val attackMode: AttackMode,
)

internal interface AttackActorResolver {
    fun resolve(
        hostStateId: GameStateId,
        session: AttackSessionState,
    ): AttackActorContext
}

internal object DefaultAttackActorResolver : AttackActorResolver {
    override fun resolve(
        hostStateId: GameStateId,
        session: AttackSessionState,
    ): AttackActorContext {
        val actorStateId = when (session.attackMode) {
            AttackMode.DIRECT -> hostStateId
            AttackMode.ZOMBIE -> session.controllerStateId ?: hostStateId
        }
        return AttackActorContext(
            hostStateId = hostStateId,
            actorStateId = actorStateId,
            attackMode = session.attackMode,
        )
    }
}
