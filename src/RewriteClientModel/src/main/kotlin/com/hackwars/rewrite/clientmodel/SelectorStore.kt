package com.hackwars.rewrite.clientmodel

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class SelectorStore<State>(initialState: State) {
    private val state = MutableStateFlow(initialState)

    fun snapshot(): State = state.value

    fun update(reducer: (State) -> State) {
        state.value = reducer(state.value)
    }

    fun <Selected> selector(project: (State) -> Selected): Flow<Selected> {
        return state.map(project).distinctUntilChanged()
    }
}
