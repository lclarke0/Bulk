package com.bulkplan.tracker.logic

import com.bulkplan.tracker.data.ChatMessage
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.net.Ai
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Sends coach messages. Lives outside any screen, so a reply still arrives
 * if you leave the chat page while the coach is thinking.
 */
object CoachChat {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> get() = _busy
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Returns false when no AI key is set, so the caller can offer copy-and-paste instead. */
    fun send(text: String): Boolean {
        if (text.isBlank() || _busy.value) return true
        if (!Ai.hasKey(Repo.state.value.settings)) return false
        Repo.update { it.copy(coach = it.coach.copy(messages = it.coach.messages + ChatMessage("user", text.trim(), display = text.trim()))) }
        call()
        return true
    }

    /** Sends the last message again after a failure. */
    fun retry() {
        if (_busy.value) return
        Repo.update { st ->
            val ms = st.coach.messages.toMutableList()
            val i = ms.indexOfLast { it.role == "user" }
            if (i < 0) return@update st
            ms[i] = ms[i].copy(failed = null)
            st.copy(coach = st.coach.copy(messages = ms.take(i + 1)))
        }
        call()
    }

    fun lastFailed(messages: List<ChatMessage>) = messages.lastOrNull { it.role == "user" }?.failed == true

    private fun call() {
        _busy.value = true
        scope.launch {
            try {
                val s = Repo.state.value
                // Fresh plan and logs go with every message as the system prompt.
                val reply = Ai.chat(s.settings, s.coach.messages, system = Coach.systemPrompt(s, Repo.logs.value))
                val prop = Coach.parseProposal(reply)
                Repo.update { it.copy(coach = it.coach.copy(messages = it.coach.messages + ChatMessage("assistant", reply), pending = prop ?: it.coach.pending)) }
            } catch (e: Exception) {
                Repo.update { st ->
                    val ms = st.coach.messages.toMutableList()
                    val i = ms.indexOfLast { it.role == "user" }
                    if (i >= 0) ms[i] = ms[i].copy(failed = true)
                    st.copy(coach = st.coach.copy(messages = ms + ChatMessage("note", e.message ?: "Couldn’t reach the AI.")))
                }
            } finally {
                _busy.value = false
            }
        }
    }
}
