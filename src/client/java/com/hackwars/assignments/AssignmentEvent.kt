package com.hackwars.assignments

import assignments.DamageAssignment
import assignments.PacketAssignment
import com.plink.dolphinnet.Assignment
import java.util.*
import javax.swing.SwingUtilities
import javax.swing.event.EventListenerList

class AssignmentEvent<T : Assignment>(source: Any, val assignment: T) : EventObject(source)

interface AssignmentListener : EventListener {}
fun interface PacketAssignmentListener : AssignmentListener {
    fun onPacketAssignment(event: AssignmentEvent<PacketAssignment>)
}

fun interface DamageAssignmentListener : AssignmentListener {
    fun onDamageAssignment(event: AssignmentEvent<DamageAssignment>)
}

interface IAssignmentEventDispatcher {
    fun <T : AssignmentListener> addAssignmentListener(c: Class<T>, l: T)
    fun <T : AssignmentListener> removeAssignmentListener(c: Class<T>, l: T)

    fun handleAssignmentPacket(assignment: Assignment): Boolean
}

class AssignmentEventDispatcher : IAssignmentEventDispatcher {
    val listeners: EventListenerList = EventListenerList()

    override fun <T : AssignmentListener> addAssignmentListener(c: Class<T>, l: T) =
        listeners.add(c, l)

    override fun <T : AssignmentListener> removeAssignmentListener(c: Class<T>, l: T) =
        listeners.remove(c, l)

    override fun handleAssignmentPacket(assignment: Assignment) = when (assignment) {
        is PacketAssignment -> fireAssignment(PacketAssignmentListener::class.java, assignment) { it, evt ->
            it.onPacketAssignment(evt)
        }.let { true }

        is DamageAssignment -> fireAssignment(DamageAssignmentListener::class.java, assignment) { it, evt ->
            it.onDamageAssignment(evt)
        }.let { true }

        else -> false
    }

    fun <L : AssignmentListener, A : Assignment> fireAssignment(
        c: Class<L>,
        a: A,
        invoke: (c: L, AssignmentEvent<A>) -> Unit
    ) {
        val evt = AssignmentEvent(this, a)
        val invoke = { listeners.getListeners(c).forEach { invoke(it, evt) } };

        if (SwingUtilities.isEventDispatchThread()) invoke() else SwingUtilities.invokeLater(invoke)
    }
}