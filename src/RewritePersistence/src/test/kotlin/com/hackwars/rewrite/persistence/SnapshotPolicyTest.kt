package com.hackwars.rewrite.persistence

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SnapshotPolicyTest {
    @Test
    fun triggersWhenEventThresholdIsReached() {
        val policy = SnapshotPolicy()

        assertTrue(policy.shouldSnapshot(eventsSinceLastSnapshot = 50, elapsedSinceLastSnapshot = 1.seconds))
    }

    @Test
    fun triggersWhenTimeThresholdIsReached() {
        val policy = SnapshotPolicy()

        assertTrue(policy.shouldSnapshot(eventsSinceLastSnapshot = 10, elapsedSinceLastSnapshot = 5.seconds))
    }

    @Test
    fun staysFalseBelowBothThresholds() {
        val policy = SnapshotPolicy()

        assertFalse(policy.shouldSnapshot(eventsSinceLastSnapshot = 49, elapsedSinceLastSnapshot = 4.seconds))
    }
}
