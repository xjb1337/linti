package com.xjb.linti.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ExamSubmissionCoordinator(private val scope: CoroutineScope) {
    private class Attempt(val token: Long) {
        var committed = false
        lateinit var job: Job
    }

    private val lock = Any()
    private var current: Attempt? = null

    fun submit(token: Long, operation: suspend (markCommitted: () -> Unit) -> Unit): Job =
        synchronized(lock) {
            current?.takeIf { it.token == token }?.let { return@synchronized it.job }
            val attempt = Attempt(token)
            attempt.job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    operation { synchronized(lock) { attempt.committed = true } }
                } finally {
                    clearIfUncommitted(attempt)
                }
            }
            current = attempt
            attempt.job.invokeOnCompletion { clearIfUncommitted(attempt) }
            attempt.job.start()
            attempt.job
        }

    private fun clearIfUncommitted(attempt: Attempt) {
        synchronized(lock) {
            if (!attempt.committed && current === attempt) current = null
        }
    }
}

internal class ExamNavigationEvent {
    private val pendingToken = MutableStateFlow<Long?>(null)
    val pending: StateFlow<Long?> = pendingToken.asStateFlow()

    fun publish(token: Long) {
        pendingToken.value = token
    }

    fun clear() {
        pendingToken.value = null
    }

    fun consume(token: Long, currentSessionToken: Long): Boolean =
        token == currentSessionToken && pendingToken.compareAndSet(token, null)
}
