package com.myra.assistant.automation

object AutomationStateManager {
    enum class State { IDLE, RUNNING, PAUSED, ERROR }

    @Volatile var state: State = State.IDLE
        private set

    fun setRunning() { state = State.RUNNING }
    fun setIdle() { state = State.IDLE }
    fun setPaused() { state = State.PAUSED }
    fun setError() { state = State.ERROR }
    fun isRunning() = state == State.RUNNING
}
