package com.xjb.linti.domain

fun interface QuizClock {
    fun nowMillis(): Long
}

object SystemQuizClock : QuizClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

data class CountdownState(
    val unlimited: Boolean,
    val remainingMillis: Long,
    val warning: Boolean,
    val expired: Boolean,
    val display: String,
)

object QuizCountdown {
    private const val MINUTE_MILLIS = 60_000L

    fun state(startedAt: Long, durationMinutes: Int, now: Long): CountdownState {
        if (durationMinutes <= 0) {
            return CountdownState(
                unlimited = true,
                remainingMillis = 0,
                warning = false,
                expired = false,
                display = "",
            )
        }
        val remaining = startedAt + durationMinutes * MINUTE_MILLIS - now
        val clamped = remaining.coerceAtLeast(0)
        val totalSeconds = clamped / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return CountdownState(
            unlimited = false,
            remainingMillis = clamped,
            warning = remaining > 0 && remaining <= 5 * MINUTE_MILLIS,
            expired = remaining <= 0,
            display = "%02d:%02d".format(minutes, seconds),
        )
    }
}
