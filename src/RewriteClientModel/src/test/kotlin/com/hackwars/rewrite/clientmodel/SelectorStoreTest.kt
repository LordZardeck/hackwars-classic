package com.hackwars.rewrite.clientmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SelectorStoreTest {
    @Test
    fun selectorEmitsOnlyWhenProjectedValueChanges() = runTest {
        val store = SelectorStore(GameViewState(counter = 1, label = "alpha"))
        val values = mutableListOf<Int>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.selector { it.counter }
                .take(3)
                .toList(values)
        }

        store.update { it.copy(label = "beta") }
        store.update { it.copy(counter = 2) }
        store.update { it.copy(counter = 2, label = "gamma") }
        store.update { it.copy(counter = 3) }
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 3), values)
    }

    private data class GameViewState(
        val counter: Int,
        val label: String,
    )
}
