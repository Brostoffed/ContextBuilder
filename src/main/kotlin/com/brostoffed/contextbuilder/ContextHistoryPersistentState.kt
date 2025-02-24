package com.brostoffed.contextbuilder

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "ContextHistory", storages = [Storage("contextHistory.xml")])
class ContextHistoryPersistentState : PersistentStateComponent<ContextHistoryPersistentState.State> {

    data class State(
        var entries: MutableList<HistoryEntryBean> = mutableListOf(),
        var markdownTemplate: String = "# {path}\n```{filetype}\n{content}\n```",
        var excludedFiletypes: MutableList<String> = mutableListOf()

    )

    private var internalState = State()

    override fun getState(): State = internalState

    override fun loadState(state: State) {
        internalState = state
    }

    companion object {
        fun getInstance(): ContextHistoryPersistentState =
            ApplicationManager.getApplication().getService(ContextHistoryPersistentState::class.java)
    }
}
